# Shipping Wave Remote

## Signing identity

Keep the same signing key for every release: Android requires it for in-place updates.
Back up the keystore and its passwords securely outside this repository. CI secrets
cannot be downloaded later. Never commit a keystore, password, or `.env` file.

The initial release preserves the private signing identity of the original development
APK, rewrapped with a random password. It is held locally under
`~/.local/share/wave-remote/` and in the repository's Actions secrets. Losing that private
key prevents compatible updates to installed copies.

For a fork, generate your own signing identity (do not reuse the original project's key):

```sh
keytool -genkeypair -keystore /private/path/release.keystore \
  -alias waveremote -keyalg RSA -keysize 4096 -validity 10000
```

Configure these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore file, without line wrapping. |
| `RELEASE_KEY_ALIAS` | Key alias in that keystore. |
| `WAVEREMOTE_STORE_PASSWORD` | Keystore password. |
| `WAVEREMOTE_KEY_PASSWORD` | Key password (often the same as store password). |

Use `gh secret set` with stdin to avoid passwords in command history. For example:

```sh
base64 -w 0 /private/path/release.keystore | gh secret set RELEASE_KEYSTORE_BASE64
```

GitHub documents [repository secrets and base64 binary blobs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
The build uses Android's [apksigner](https://developer.android.com/tools/apksigner)
to sign and verify every APK, including the public certificate fingerprint.

## Local release build

Set `RELEASE_KEYSTORE`, `RELEASE_KEY_ALIAS`, `WAVEREMOTE_STORE_PASSWORD`, and
`WAVEREMOTE_KEY_PASSWORD` in a private shell environment, then run:

```sh
./test.sh
./build.sh release
```

The build uses the Node.js version in `.node-version` and invokes the installed
Expo CLI directly. For release packaging, install dependencies with
`npm ci --ignore-scripts --no-audit --no-fund`, as the F-Droid recipe does.
Development dependency management and app tests use Bun. The CI checks and release
packaging run in separate jobs; the packaging job does not install Bun.

Release signing preserves ZIP alignment and disables legacy v1/JAR signatures.
The minimum Android version is 7.0 (API 24), which supports the v2/v3 signatures.
Removing unnecessary v1 entries also avoids Python 3.13 ZIP-flag changes during
F-Droid signature copying. Use [the F-Droid verification script](../tools/verify-fdroid.sh)
against each production release and independent rebuild before publication.

The release path never creates or substitutes a signing key. Debug builds use a
separate cached key. The package ID remains `me.vwave.remote`.

## Publish a version

Do every step, in order. A version isn't done until the download site shows it.

1. Update `VERSION` (`major.minor` or `major.minor.patch`) and increase the positive integer in `VERSION_CODE`. Android uses this independent counter to determine update order.
2. Date the `## Unreleased` heading in `CHANGELOG.md` (`## 0.4 — 2026-10-16`) and rewrite `docs/release-notes.md` for this version: what changed, whether it installs over the previous version and keeps the pairing, and the `sha256sum --check wave-remote-v<version>.apk.sha256` line. Add the store changelog, `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt`: the same changes as a short plain list, under 500 characters.
3. Run `bun run typecheck`, `bun run lint`, `bun test`, `./test.sh` and `python3 tools/npm-lock.py --check`. Build with `./build.sh release` (or `debug` without the signing key), install it on a real phone with `adb install -r`, and try each changelog item against a real TV.
4. Refresh the screenshots from that phone, connected and showing the TV name and volume:
   - `docs/screenshots/buttons.png` and `touchpad.png`: `adb exec-out screencap -p`, cropped to 1080×2170 from the top of the header to the bottom of the Back/Home/Settings row (on a 1080×2400 Pixel 7, `crop=1080:2170:0:140`).
   - `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png` and `2.png`: copies of `buttons.png` and `touchpad.png`, for F-Droid and IzzyOnDroid.
   - `site/public/screens/*.webp`: scaled from those to 720 px wide, e.g. `ffmpeg -i ../docs/screenshots/buttons.png -vf scale=720:-1:flags=lanczos -c:v libwebp -quality 85 buttons.webp`. Update the version and date in each `.webp.json` sidecar.
   - `site/public/og.png`: re-render with `node og/render.cjs` (see [site/README.md](../site/README.md)) and look at the result.
5. Commit and push `main`, then create and push the matching tag:

```sh
git tag -a "v$(cat VERSION)" -m "Wave Remote $(cat VERSION)"
git push origin "v$(cat VERSION)"
```

The release workflow checks the tag against `VERSION`, runs tests, restores the
private keystore to a temporary runner file, builds and verifies the release APK,
and publishes the APK, `.sha256`, and `.certificate.txt` to GitHub Releases.
The temporary keystore is removed even when a step fails.

6. Deploy the site while the workflow runs: `cd site && bun install && bun run typecheck && bun run deploy`.
7. Once the release is published, verify it: download the APK and `.sha256` into one directory, run `sha256sum --check <filename>.apk.sha256`, and compare the certificate SHA-256 digest in `.certificate.txt` with the previous release's.
8. Check the site: `curl -fsS https://remote.vwave.me/latest.json` shows the new version (it caches GitHub for up to ten minutes), `curl -fsSI https://remote.vwave.me/download` redirects to the new APK, and `/screens/buttons.webp` and `/og.png` match the committed files.

If a release run fails before publishing, fix the cause and rerun the failed job.
Do not move published tags or replace an already released APK; ship a new version.
