#!/usr/bin/env python3
"""Mock webOS TV for testing TV Remote: SSAP over wss://:3001, pointer socket, SSDP responder.

Implements the protocol as observed statically in LG ThinQ 5.2.12500 (see NOTICE.md).
Every message is logged as one JSON line so tests can assert on it.
"""
import asyncio, base64, hashlib, json, os, socket, ssl, struct, subprocess, sys, time

HOST_IP = sys.argv[1] if len(sys.argv) > 1 else "0.0.0.0"
HERE = os.path.dirname(os.path.abspath(__file__))
CERT, KEY = os.path.join(HERE, "mock_tv.crt"), os.path.join(HERE, "mock_tv.key")
CLIENT_KEY = "mock-client-key-0001"
LOG = open(os.path.join(HERE, "mock_tv.log"), "a", buffering=1)

state = {"volume": 12, "muted": False, "input": "HDMI_1", "app": "com.webos.app.home"}
subs = []  # (writer, id) for audio/getVolume


def log(kind, **kw):
    LOG.write(json.dumps({"t": round(time.time(), 3), "kind": kind, **kw}) + "\n")
    print(kind, kw, flush=True)


def ensure_cert():
    if not os.path.exists(CERT):
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "3650",
                        "-keyout", KEY, "-out", CERT, "-subj", "/CN=mock-webos-tv"], check=True,
                       capture_output=True)


async def ws_handshake(reader, writer):
    request = (await reader.readuntil(b"\r\n\r\n")).decode()
    path = request.split(" ")[1]
    key = next(l.split(":", 1)[1].strip() for l in request.split("\r\n") if l.lower().startswith("sec-websocket-key"))
    accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
    writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  f"Sec-WebSocket-Accept: {accept}\r\n\r\n").encode())
    await writer.drain()
    return path


async def read_frame(reader):
    b0, b1 = await reader.readexactly(2)
    op, n = b0 & 0x0F, b1 & 0x7F
    if n == 126:
        n = struct.unpack(">H", await reader.readexactly(2))[0]
    elif n == 127:
        n = struct.unpack(">Q", await reader.readexactly(8))[0]
    if not b1 & 0x80:
        raise ValueError("client frame not masked")
    mask = await reader.readexactly(4)
    data = bytes(c ^ mask[i % 4] for i, c in enumerate(await reader.readexactly(n)))
    return op, data


def send(writer, obj):
    data = json.dumps(obj).encode()
    n = len(data)
    head = bytes([0x81, n]) if n < 126 else bytes([0x81, 126]) + struct.pack(">H", n) if n < 65536 \
        else bytes([0x81, 127]) + struct.pack(">Q", n)
    writer.write(head + data)
    log("tx", msg=obj)


def volume_payload():
    return {"returnValue": True, "volumeStatus": {"volume": state["volume"], "muteStatus": state["muted"]}}


def push_volume():
    for w, sid in list(subs):
        if w.is_closing():
            subs.remove((w, sid))
        else:
            send(w, {"type": "response", "id": sid, "payload": volume_payload()})


async def handle_control(reader, writer, peer):
    while True:
        op, data = await read_frame(reader)
        if op == 0x8:
            return
        if op == 0x9:
            writer.write(bytes([0x8A, len(data)]) + data)
            log("ping")
            continue
        if op != 0x1:
            continue
        msg = json.loads(data)
        log("rx", peer=peer, msg=msg if msg.get("type") != "register" else {**msg, "payload": {
            "client-key": msg["payload"].get("client-key"),
            "manifest.signed.appId": msg["payload"]["manifest"].get("signed", {}).get("appId"),
            "manifest.signatures": len(msg["payload"]["manifest"].get("signatures", [])),
            "manifest.permissions": len(msg["payload"]["manifest"].get("permissions", []))}})
        mid, uri, p = msg.get("id"), msg.get("uri", ""), msg.get("payload") or {}
        t = msg.get("type")
        if t == "register":
            if p.get("client-key") != CLIENT_KEY:
                send(writer, {"type": "response", "id": mid, "payload": {"pairingType": "PROMPT", "returnValue": True}})
                await writer.drain()
                await asyncio.sleep(3)  # user pressing "Accept" on the TV
            send(writer, {"type": "registered", "id": mid, "payload": {"client-key": CLIENT_KEY}})
        elif uri == "ssap://audio/getVolume":
            if t == "subscribe":
                subs.append((writer, mid))
            send(writer, {"type": "response", "id": mid, "payload": volume_payload()})
        elif uri in ("ssap://audio/volumeUp", "ssap://audio/volumeDown"):
            state["volume"] = max(0, min(100, state["volume"] + (1 if uri.endswith("Up") else -1)))
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True}})
            push_volume()
        elif uri == "ssap://audio/setMute":
            state["muted"] = bool(p.get("mute"))
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True}})
            push_volume()
        elif uri == "ssap://com.webos.service.connectionmanager/getinfo":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True,
                 "wiredInfo": {"macAddress": "a8:23:fe:00:00:01"}, "wifiInfo": {"macAddress": "a8:23:fe:00:00:02"}}})
        elif uri == "ssap://tv/getExternalInputList":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True, "devices": [
                {"id": "HDMI_1", "label": "PlayStation 5", "connected": True},
                {"id": "HDMI_2", "label": "HDMI 2", "connected": False},
                {"id": "HDMI_3", "label": "Apple TV", "connected": True}]}})
        elif uri == "ssap://com.webos.applicationManager/listLaunchPoints":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True, "launchPoints": [
                {"id": "netflix", "title": "Netflix"}, {"id": "youtube.leanback.v4", "title": "YouTube"},
                {"id": "com.webos.app.livetv", "title": "Live TV"}]}})
        elif uri == "ssap://com.webos.service.networkinput/getPointerInputSocket":
            ip = writer.get_extra_info("sockname")[0]
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True,
                 "socketPath": f"wss://{ip}:3001/resources/0123456789abcdef/netinput.pointer.sock"}})
        elif uri == "ssap://com.webos.service.tvpower/power/getPowerState":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True, "state": "Active"}})
        elif uri == "ssap://settings/getSystemSettings":
            known = {"deviceName": "[LG] Mock LG TV", "wolwowlOnOff": "true"}
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True, "category": p.get("category"),
                 "settings": {k: known[k] for k in p.get("keys", []) if k in known}}})
        elif uri == "ssap://com.webos.service.asc/registerDevice":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True}})
        elif uri == "ssap://system/turnOff":
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True}})
            await writer.drain()
            writer.close()
            return
        else:
            send(writer, {"type": "response", "id": mid, "payload": {"returnValue": True}})
        await writer.drain()


async def handle_pointer(reader, writer, peer):
    while True:
        op, data = await read_frame(reader)
        if op == 0x8:
            return
        log("pointer", peer=peer, frame=data.decode())


async def on_connect(reader, writer):
    peer = writer.get_extra_info("peername")[0]
    try:
        path = await ws_handshake(reader, writer)
        log("open", peer=peer, path=path)
        if path.endswith("netinput.pointer.sock"):
            await handle_pointer(reader, writer, peer)
        else:
            await handle_control(reader, writer, peer)
    except (asyncio.IncompleteReadError, ConnectionError, ssl.SSLError):
        pass
    finally:
        log("close", peer=peer)
        writer.close()


class Ssdp(asyncio.DatagramProtocol):
    def connection_made(self, t):
        self.t = t

    def datagram_received(self, data, addr):
        if b"M-SEARCH" in data and b"webos-second-screen" in data:
            log("ssdp", peer=addr[0])
            ip = HOST_IP if HOST_IP != "0.0.0.0" else socket.gethostbyname(socket.gethostname())
            self.t.sendto((f"HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://{ip}:1990/desc.xml\r\n"
                           "ST: urn:lge-com:service:webos-second-screen:1\r\nSERVER: WebOS/4.1.0 UPnP/1.0\r\n\r\n").encode(), addr)


async def desc(reader, writer):
    await reader.readuntil(b"\r\n\r\n")
    body = (b"<root><device><friendlyName>[LG] Mock LG TV</friendlyName>"
            b"<bluetoothMac>a8:23:fe:00:00:03</bluetoothMac></device></root>")
    writer.write(b"HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: %d\r\n\r\n" % len(body) + body)
    await writer.drain()
    writer.close()


async def main():
    ensure_cert()
    ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    ctx.load_cert_chain(CERT, KEY)
    await asyncio.start_server(on_connect, "0.0.0.0", 3001, ssl=ctx)
    await asyncio.start_server(desc, "0.0.0.0", 1990)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", 1900))
    mreq = struct.pack("4s4s", socket.inet_aton("239.255.255.250"), socket.inet_aton(HOST_IP if HOST_IP != "0.0.0.0" else "0.0.0.0"))
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
    await asyncio.get_running_loop().create_datagram_endpoint(Ssdp, sock=sock)
    log("listening", ip=HOST_IP)
    await asyncio.Event().wait()


asyncio.run(main())
