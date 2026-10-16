# Contributing

Small, focused changes are welcome. Open an issue before a large UI or protocol change.
Keep the app local: no account requirements, analytics, ads, or cloud services. Prefer
Expo and React Native Paper components over new dependencies, and keep protocol code in
the Java engine under `src/me/vwave/remote`.

Run `bun run typecheck`, `bun run lint`, `bun test`, and `./test.sh` before submitting a
change. For Android compilation, set `ANDROID_HOME` and `JAVA_HOME`, then run
`./build.sh debug`. See [the README](README.md#building).
PR CI runs the same checks and uploads a debug APK without using release secrets.

For manual protocol work, `python3 tools/mock_tv.py <host-ip>` runs a mock TV and
`tools/ssap_probe.py` sends single requests to a real one. `tools/build-legacy.sh`
builds the original framework-only Java app for comparison.

For protocol bugs, include your TV model, firmware/webOS version, phone, Android version,
and app version. Scrub client keys, device addresses, and personal information from logs.
Tests use synthetic keys and a local mock; do not add real pairing data.

Screenshots in `docs/screenshots` are from the app on a Pixel 7. Branding source is in
`docs/brand`; keep the dark ink, blue accent, and coral power glyph consistent.
