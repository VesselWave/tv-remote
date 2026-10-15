# Contributing

Small, focused changes are welcome. Open an issue before a large UI or protocol change.
Keep the APK framework-only: no third-party runtime libraries, account requirements,
analytics, or cloud services.

Run `./test.sh` before submitting a change. For Android compilation, set `ANDROID_HOME`
and `JAVA_HOME`, then run `./build.sh debug`. See [the README](README.md#build-it-yourself).
PR CI runs the same checks and uploads a debug APK without using release secrets.

For protocol bugs, include your TV model, firmware/webOS version, phone, Android version,
and app version. Scrub client keys, device addresses, and personal information from logs.
Tests use synthetic keys and a local mock; do not add real pairing data.

Screenshots in `docs/screenshots` are from the app on a Pixel 7. Branding source is in
`docs/brand`; keep the dark ink, blue accent, and coral power glyph consistent.
