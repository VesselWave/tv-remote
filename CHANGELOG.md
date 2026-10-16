# Changelog

## 0.5 — 2026-10-16

- The APK is about 13 MB, down from 31 MB.
- Asks for fewer Android permissions: no more "display over other apps" or storage access, which the app never used.
- A TV the app has already paired with over an encrypted connection is never contacted unencrypted again. Older TVs that only support unencrypted connections still work.
- Looking for TVs only reads device details from the device that answered.
- Builds no longer contain the build machine's network address.

## 0.4 — 2026-10-16

- Remembers more than one TV. Use the switch button next to the remote's settings to change TVs or add another; each TV keeps its own pairing and wake-up details. Forgetting a TV only removes that one.
- Your existing pairing carries over.
- Give a TV its own name on this phone, to tell apart several of the same model: tap the pencil next to it in the TV list. Leave it empty to go back to the TV's name.
- The `.N` IP shorthand now uses the subnet of a TV the app already knows.
- The input list says “Connected” under a port with something plugged in, or “Connected, no signal” if that device sends nothing, instead of the port’s ID.

## 0.3 — 2026-10-16

- Renamed from TV Remote to Wave Remote. The package ID is now `me.vwave.remote` and the download site is [remote.vwave.me](https://remote.vwave.me).
- Because the package ID changed, this version installs alongside 0.2 instead of updating it. Pair your TV again, then uninstall the old app.

## 0.2 — 2026-10-15

The remote is rebuilt with Expo and React Native, keeping the same layout and protocol engine.

- Icons on the remote's buttons, in the outline style of LG ThinQ's TV control widget: inputs, mute/unmute, apps, keyboard, channel up/down, the Buttons/Touchpad switch, back, home and TV settings. Larger buttons keep a short caption.
- Material setup, settings, app and input pickers, and keyboard sheets. Apps are searchable.
- Wider volume and channel rockers. The touchpad fills the space below the controls, with a wider scroll grip.
- Hold-to-repeat stops when the app goes to the background. Haptic feedback on presses.
- Runs on Android 7.0 or newer (was 9). The APK is now about 30 MB, arm64 only.
- Updates 0.1 in place and keeps your pairing.

## 0.1 — 2026-10-15

First standalone public preview release.

- Discover and pair with LG webOS TVs on your local network.
- D-pad with hold-to-repeat, touchpad, dedicated scroll strip, and two-finger scrolling.
- Natural and traditional scroll directions, saved between sessions.
- Volume, mute, channels, media controls, inputs, apps, and keyboard text entry.
- Wake-on-LAN and Bluetooth LE wake-up on supported TVs.
- Local certificate pinning and saved pairing; no account or cloud service.
- Signed universal APKs, SHA-256 checksums, and certificate details on releases.

Requires Android 9 or newer. Tested on LG 65UT7570PUB with webOS 24 and Pixel 7;
other models and firmware versions may differ. See the README for wake settings.
