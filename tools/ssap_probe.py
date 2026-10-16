#!/usr/bin/env python3
"""Talk SSAP to a real webOS TV using Wave Remote's stored client key, so no new pairing prompt.

usage: ssap_probe.py <tv-ip> <keyfile> <step>...
  step:  ssap://uri[=json-payload]     request, print the response
         sub:ssap://uri[=json]         subscribe, print the first response
         ptr:<frame>                   send a pointer-socket frame, e.g. 'ptr:type:move\\ndx:40\\ndy:0\\ndown:0'
         sleep:<seconds>
The key file holds only the client key. It is read locally and sent only to the TV.
"""
import base64, json, os, socket, ssl, struct, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))


class Ws:
    def __init__(self, host, port, path):
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE  # the app pins this cert; the probe only reads
        self.s = ctx.wrap_socket(socket.create_connection((host, port), timeout=10))
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall((f"GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                        f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.s.recv(1)
        if b" 101 " not in head.split(b"\r\n")[0]:
            raise SystemExit("handshake refused: " + head.decode(errors="replace"))

    def send(self, text):
        data = text.encode()
        n = len(data)
        hdr = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n)
                               if n < 65536 else bytes([0x80 | 127]) + struct.pack(">Q", n))
        mask = os.urandom(4)
        self.s.sendall(hdr + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise EOFError("closed by TV")
            buf += chunk
        return buf

    def recv(self):
        while True:
            b0, b1 = self._exact(2)
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._exact(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._exact(8))[0]
            data = self._exact(n)
            if b0 & 0x0F == 1:
                return json.loads(data)
            if b0 & 0x0F == 8:
                raise EOFError("closed by TV")


def main():
    host, keyfile, steps = sys.argv[1], sys.argv[2], sys.argv[3:]
    manifest = json.load(open(os.path.join(HERE, "manifest.json")))
    ws = Ws(host, 3001, "/")
    ws.send(json.dumps({"type": "register", "id": "reg0", "payload": {
        "client-key": open(keyfile).read().strip(), "forcePairing": False, "pairingType": "PROMPT", "manifest": manifest}}))
    while True:
        m = ws.recv()
        if m.get("type") == "registered":
            break
        if m.get("type") == "error" or "pairingType" in (m.get("payload") or {}):
            raise SystemExit("not registered: " + json.dumps(m))
    print("registered", flush=True)
    ptr = None
    for i, step in enumerate(steps):
        if step.startswith("sleep:"):
            time.sleep(float(step[6:]))
            continue
        if step.startswith("ptr:"):
            if ptr is None:
                ws.send(json.dumps({"type": "request", "id": "ptr", "uri": "ssap://com.webos.service.networkinput/getPointerInputSocket"}))
                while (m := ws.recv()).get("id") != "ptr":
                    pass
                path = m["payload"]["socketPath"]
                hostport, p = path.split("://", 1)[1].split("/", 1)
                ptr = Ws(host, int(hostport.split(":")[1]), "/" + p)
            frame = step[4:].encode().decode("unicode_escape") + "\n\n"
            ptr.send(frame)
            print("ptr>", frame.replace("\n", "\\n"), flush=True)
            continue
        kind = "subscribe" if step.startswith("sub:") else "request"
        uri, _, payload = step.removeprefix("sub:").partition("=")
        rid = f"r{i}"
        ws.send(json.dumps({"type": kind, "id": rid, "uri": uri, "payload": json.loads(payload) if payload else {}}))
        while (m := ws.recv()).get("id") != rid:
            pass
        print(uri, json.dumps(m.get("payload", m), ensure_ascii=False)[:3000], flush=True)


if __name__ == "__main__":
    main()
