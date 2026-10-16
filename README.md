<p align="center">
  <img src="docs/brand/cover.svg" alt="Wave Remote: control your LG webOS TV from your Android phone, over your own Wi-Fi." width="100%">
</p>

<p align="center">
  <a href="https://github.com/VesselWave/wave-remote/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/VesselWave/wave-remote?style=flat-square&amp;color=3d7bff"></a>
  <a href="https://github.com/VesselWave/wave-remote/actions/workflows/ci.yml"><img alt="Checks" src="https://github.com/VesselWave/wave-remote/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-3d7bff?style=flat-square"></a>
</p>

Wave Remote is an Android app for controlling an LG webOS TV over your home network. It talks to the TV directly: no account, no cloud service, no ads, no analytics.

**[Download the APK](https://remote.vwave.me)** · [Changelog](CHANGELOG.md)

<p align="center">
  <img src="docs/screenshots/buttons.png" alt="Buttons mode: power, inputs, mute, volume and channel rockers, apps, keyboard, playback and a D-pad" width="32%">
  &nbsp;&nbsp;
  <img src="docs/screenshots/touchpad.png" alt="Touchpad mode with a scroll strip on the right edge" width="32%">
</p>

## Features

- D-pad with hold-to-repeat, or a touchpad with a scroll strip and two-finger scrolling
- Volume, mute, channels, playback, inputs, apps and keyboard input
- Your phone's volume buttons control the TV
- Turn the TV on (Wake-on-LAN or Bluetooth) and off
- Finds TVs on your network, or connects by IP address

## Install

1. Install the APK on an Android 7+ phone (arm64).
2. Connect the phone to the same network as the TV. Guest Wi-Fi and client isolation can get in the way.
3. Open the app, pick your TV and accept the pairing prompt on the TV. The prompt may say "LG Mobile" ([why](NOTICE.md)).

Each release comes with a `.sha256` checksum and the signing certificate.

### Turning the TV on

Connect once while the TV is on so the app learns its wake addresses, and enable **Mobile TV On** (or **Turn on via Wi-Fi**) in the TV's settings. Bluetooth wake also needs the Nearby devices permission on Android 12+. A TV that's unplugged can't be woken.

## Compatibility

Tested with an LG 65UT7570PUB (webOS 24) and a Pixel 7. Other webOS TVs may work but haven't been checked. Android TV, Samsung, Roku and infrared aren't supported.

| Problem | Try |
| --- | --- |
| No TV found | Enter its IP address. Make sure the phone isn't on guest Wi-Fi or a VPN. |
| Pairing rejected | Reconnect and accept the prompt on the TV. |
| "TV certificate changed" | Forget the TV in the app menu and pair again. A factory reset changes the certificate. |
| Turning on doesn't work | Enable Mobile TV On and connect once while the TV is on. |
| Keyboard does nothing | Select a text field on the TV first. |

## Privacy

The app only talks to your TV. There's no backend, telemetry or crash reporting. Pairing keys and settings stay in the app's private storage, and Android backup is off. It asks for network and Wi-Fi multicast access to find and control the TV, and Bluetooth for the optional wake-up.

Newer TVs use TLS, with the certificate pinned at first pairing. Older ones may fall back to unencrypted WebSockets, so use a network you trust. To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Building

You need Bun, JDK 17+, Python 3 and the Android SDK (platform 36, build-tools 36.0.0), with `ANDROID_HOME` and `JAVA_HOME` set.

```sh
bun install
bun run typecheck && bun run lint && bun test
./test.sh           # protocol tests against a mock TV
./build.sh debug    # writes dist/wave-remote-v<version>-debug.apk
```

The UI is Expo and React Native. The TV protocol is a Java engine in `src/me/vwave/remote`, wrapped by the Expo module in `modules/wave-remote`. Expo Go can't load that module, so install the built APK.

See [CONTRIBUTING.md](CONTRIBUTING.md) for development, [docs/RELEASING.md](docs/RELEASING.md) for signed releases and [docs/PROTOCOL.md](docs/PROTOCOL.md) for protocol notes. The download site lives in [`site/`](site/README.md).

## License

MIT; see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Wave Remote is an independent project, not affiliated with or endorsed by LG Electronics. LG and webOS are trademarks of their owners.
