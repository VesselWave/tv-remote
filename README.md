<p align="center">
  <img src="docs/brand/cover.svg" alt="TV Remote — Less app. More remote. A tiny, private Android remote for LG webOS TVs." width="100%">
</p>

<p align="center">
  <a href="https://github.com/VesselWave/tv-remote/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/VesselWave/tv-remote?style=flat-square&amp;color=3d7bff"></a>
  <a href="https://github.com/VesselWave/tv-remote/actions/workflows/ci.yml"><img alt="Checks and APK" src="https://github.com/VesselWave/tv-remote/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Android 9 or newer" src="https://img.shields.io/badge/Android-9%2B-3d7bff?style=flat-square">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-3d7bff?style=flat-square"></a>
</p>

<p align="center"><b>Your phone. Your TV. Your network.</b><br>
Control your LG webOS TV without an account, cloud service, ads, or analytics.<br>
About 53 KB. Native Java. Zero third-party runtime libraries.</p>

<p align="center"><a href="https://github.com/VesselWave/tv-remote/releases/latest"><b>↓ Download the APK</b></a> · <a href="#get-connected">Setup</a> · <a href="#build-it-yourself">Build</a> · <a href="CHANGELOG.md">Changelog</a></p>

## A remote, without the baggage

- **Two ways to navigate.** Familiar D-pad buttons with hold-to-repeat, or a touchpad: drag to move the pointer and tap to click.
- **Scroll comfortably.** A dedicated right-edge scroll strip and two-finger scrolling. Choose Natural or Traditional under **⋯ → Scroll direction**.
- **The everyday controls.** Volume, mute, channels, playback, input switching, app launching, and keyboard text entry. Your phone’s volume buttons work too.
- **Wake your TV.** Wake-on-LAN and Bluetooth LE wake-up on supported TVs. Power off from the same screen.
- **Pair once.** Discover TVs automatically or enter an IP address. Accept the prompt on your TV; the pairing key stays on your phone.
- **Keep it local.** Direct TV connections, certificate pinning after first pairing, and no online account.

## See it in action

<p align="center">
  <img src="docs/screenshots/buttons.png" alt="TV Remote on a Pixel 7: directional buttons, power, inputs, volume, channels, and media controls" width="32%">
  &nbsp;&nbsp;
  <img src="docs/screenshots/touchpad.png" alt="TV Remote touchpad mode with a dedicated vertical scroll strip" width="32%">
</p>
<p align="center"><sub>Real app screenshots on Pixel 7. Buttons on the left, touchpad on the right.</sub></p>

## Get connected

1. Download the `.apk` from the [latest release](https://github.com/VesselWave/tv-remote/releases/latest) on your **Android 9+** phone. Allow installation from the browser or file manager when Android asks.
2. Put your phone and TV on the **same local network**. The TV can use Ethernet while the phone uses Wi-Fi. Guest networks and client isolation can block discovery and control.
3. Turn on the TV, open TV Remote, and select your TV. If discovery misses it, enter the TV’s IP address from its network settings.
4. Accept the pairing prompt **on the TV**. It may identify the client as **LG Mobile** because of the protocol registration manifest; see [NOTICE.md](NOTICE.md).

To verify a download, put its `.sha256` file beside the APK and run:

```sh
sha256sum --check tv-remote-v0.1.apk.sha256
```

Use the filename for your version. Every release also includes the APK signing certificate details. Future official releases use the same signing identity so they can install as updates. Locally built debug APKs use a different key and cannot update official APKs in place.

### Make power-on work

Connect once while the TV is on so the app can learn its wake addresses. Enable **Mobile TV On / Turn on via Wi-Fi** in the TV’s settings (names and menus vary by model).

Wake-on-LAN uses the TV’s saved wired/Wi-Fi MAC addresses. Bluetooth wake also needs supported phone/TV hardware, Bluetooth enabled, and **Nearby devices** permission on Android 12+. Permission is requested when first needed. The app advertises a wake packet; it does not scan for nearby Bluetooth devices or request location access.

Wake-up depends on the TV’s standby settings and firmware. A TV unplugged from power cannot be woken.

### Compatibility and troubleshooting

Tested with **LG 65UT7570PUB, webOS 24 / firmware 33.31.75**, and a **Pixel 7**. Other LG webOS models may work, but have not been verified. This app is for LG webOS, not Android TV, Samsung, Roku, or infrared control.

| What you see | What to try |
| --- | --- |
| No TV found | Use its IP address; check the same LAN, guest Wi-Fi, VPN routing, and client isolation. |
| Pairing rejected | Reconnect and accept the TV prompt. If needed, forget the TV through the app menu and pair again. |
| Certificate changed | Confirm the TV and network are trusted, then forget and re-pair. A factory reset can change the certificate. |
| Power off works, wake does not | Enable Mobile TV On, connect while the TV is on, and check Bluetooth permission. |
| Keyboard does nothing | Select a TV text field first. Some apps do not accept remote text entry. |
| Cursor acceleration differs from the physical remote | D-pad arrows use a modest repeat ramp; they do not reproduce the physical remote’s native acceleration. |

## Privacy, in plain English

There is no developer backend, telemetry, crash reporting, advertising SDK, or account. The app connects to your TV over your LAN and optionally advertises a Bluetooth wake packet. Pairing keys, certificate fingerprints, TV addresses, and preferences are stored in the app’s private storage; Android backup is disabled.

| Android permission | Why it is used |
| --- | --- |
| Internet | Local TV sockets and HTTP device descriptions. Android requires this permission for LAN sockets too. |
| Wi-Fi state / multicast | Discover TVs over SSDP. |
| Nearby devices / Bluetooth advertise | Optional Bluetooth LE wake-up. |
| Legacy Bluetooth permissions | Bluetooth wake support on Android 9–11. |

Newer TVs use TLS with trust on first use and certificate pinning. Older TVs may fall back to unencrypted WebSockets on port 3000; discovery descriptions use HTTP, and the TV chooses the pointer socket transport. Use a trusted home network. See [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Build it yourself

Requires **JDK 17+** (CI uses 21), **Python 3**, and Android SDK **platform 35 / build-tools 35.0.0**. Tests also need `curl`, `openssl`, and `sha256sum`. No Gradle, package manager, or third-party Android dependencies.

```sh
export ANDROID_HOME="$HOME/opt/android-sdk"  # or your SDK location
export JAVA_HOME="/path/to/jdk-21"
./test.sh
./build.sh debug
adb install -r dist/tv-remote-v0.1-debug.apk
```

The SDK build tools compile resources, compile Java, dex, align, sign, and verify the APK. Debug signing uses a separate cached development key. `VERSION` sets the displayed version and release tag (`0.1` → `v0.1`). `VERSION_CODE` is Android’s independent update counter; increase it for every release, including prereleases.

`./test.sh` runs the real protocol client against a temporary mock TV, covering first pairing, saved-key reconnect, certificate pin rejection, pointer frames, input lists, power transitions, SSDP discovery, wake-packet bytes, and held-key timing. It fetches a checksum-pinned JSON-java JAR for JVM tests only; Android supplies JSON APIs in the app. Tests use ports 1900, 1990, and 3001 and clean up the mock on exit.

For manual protocol work, run `python3 tools/mock_tv.py <your-host-ip>`. `tools/ssap_probe.py` sends individual requests; `tools/tvshot.sh` captures the TV screen. These developer tools are not part of the APK.

## Releases and contributing

Pull requests and pushes to `main` run tests and upload a debug APK. Pushing a `v<version>` tag runs tests, builds a signed release, verifies the APK, and publishes it with a SHA-256 checksum and signing certificate report. Missing release credentials fail the build.

See [RELEASING.md](docs/RELEASING.md) for signing setup and the release checklist, [CONTRIBUTING.md](CONTRIBUTING.md) for development, and [CHANGELOG.md](CHANGELOG.md) for changes.

## Credits and license

Created by [VesselWave](https://github.com/VesselWave). Extracted from the private rea-stuff research workspace. The public [protocol notes](docs/PROTOCOL.md) describe the implementation and tested behavior. Original source and branding are [MIT licensed](LICENSE); protocol provenance and third-party notices are in [NOTICE.md](NOTICE.md).

**Independent project. Not affiliated with or endorsed by LG Electronics.** LG and webOS are trademarks of their respective owners.
