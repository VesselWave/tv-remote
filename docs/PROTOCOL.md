# Local webOS protocol

Notes on the local protocol Wave Remote speaks, worked out against an LG 65UT7570PUB
(webOS 24, firmware 33.31.75). This isn't an official LG specification, and other
models and firmware may differ.

## Connection and pairing

The control channel is a WebSocket over TLS at port **3001**. If that connection
is refused, the client tries legacy unencrypted WebSockets at port **3000**.
A self-signed TV certificate is trusted on first pairing; its SHA-256 fingerprint
is persisted after successful registration and checked on later connections.
The app does not implement ThinQ's additional vendor verification exchange.

Registration sends the saved `client-key`, when available, and the signed
“LG Mobile” manifest observed in ThinQ 5.2.12500. First pairing uses the TV's
on-screen prompt. A successful `registered` response supplies the client key.
A rejected stored key is cleared so the next attempt can pair again.
The registration signature is public protocol data, distinct from APK signing.

## Commands and pointer socket

| Purpose | SSAP endpoint |
| --- | --- |
| Volume subscription | `ssap://audio/getVolume` |
| Input list | `ssap://tv/getExternalInputList` |
| App list | `ssap://com.webos.applicationManager/listLaunchPoints` |
| Pointer socket | `ssap://com.webos.service.networkinput/getPointerInputSocket` |
| Power state | `ssap://com.webos.service.tvpower/power/getPowerState` |
| Power off | `ssap://system/turnOff` |
| Network MAC addresses | `ssap://com.webos.service.connectionmanager/getinfo` |

The TV provides a separate pointer socket URL. Frames contain newline-separated
fields with an empty final line, for example `type:button` with `name:UP`.
Movement uses `type:move` with `dx`, `dy`, and `down:0`; click uses `type:click`;
scroll uses `type:scroll` with `dx` and `dy`.

On the tested TV, each scroll frame represents a wheel notch regardless of the
numeric magnitude; negative `dy` scrolls down. Opening a new pointer socket resets
the TV cursor to the screen center. D-pad controls continue to send arrow buttons
rather than pointer movement.

## Discovery and wake-up

SSDP searches use `urn:lge-com:service:webos-second-screen:1`. Device descriptions
are HTTP XML and include a friendly name and, on supported TVs, `bluetoothMac`.

Wake-on-LAN uses six `FF` bytes followed by sixteen repetitions of the target MAC,
sent three times to UDP port 9, both to the TV's saved address and broadcast targets.
Wired and Wi-Fi MACs are learned while connected.

Bluetooth wake registration calls `ssap://com.webos.service.asc/registerDevice`
with `{type: "ThinQ", address: <random locally administered phone MAC>}`.
The app advertises manufacturer ID **0x00C4**, with payload
`phoneAddr[6] | 0x07 | 0x00 | tvBtMac[6]`, and service UUID
`0000feba-0000-1000-8000-00805f9b34fb`. It advertises only; it does not scan.

On that TV, Wake-on-LAN wakes it in about four seconds and BLE alone in about seven.
Both depend on TV standby settings and supported hardware.

## Standby detection

The tested TV may keep the control socket half-open and answer pings while going
to standby. The power-state subscription reports `processing: "Request Power Off"`
before reaching `state: "Suspend"`. Both signals are used, with WebSocket keep-alive
and a silence timeout as a fallback.
