# Shipping TV Remote

## Signing identity

Keep the same signing key for every release: Android requires it for in-place updates.
Back up the keystore and its passwords securely outside this repository. CI secrets
cannot be downloaded later. Never commit a keystore, password, or `.env` file.

The initial release preserves the private signing identity of the original development
APK, rewrapped with a random password. It is held locally under
`~/.local/share/tv-remote/` and in the repository's Actions secrets. Losing that private
key prevents compatible updates to installed copies.

For a fork, generate your own signing identity (do not reuse the original project's key):

```sh
keytool -genkeypair -keystore /private/path/release.keystore \
  -alias tvremote -keyalg RSA -keysize 4096 -validity 10000
```

Configure these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore file, without line wrapping. |
| `RELEASE_KEY_ALIAS` | Key alias in that keystore. |
| `TVREMOTE_STORE_PASSWORD` | Keystore password. |
| `TVREMOTE_KEY_PASSWORD` | Key password (often the same as store password). |

Use `gh secret set` with stdin to avoid passwords in command history. For example:

```sh
base64 -w 0 /private/path/release.keystore | gh secret set RELEASE_KEYSTORE_BASE64
```

GitHub documents [repository secrets and base64 binary blobs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
The build uses Android's [apksigner](https://developer.android.com/tools/apksigner)
to sign and verify every APK, including the public certificate fingerprint.

## Local release build

Set `RELEASE_KEYSTORE`, `RELEASE_KEY_ALIAS`, `TVREMOTE_STORE_PASSWORD`, and
`TVREMOTE_KEY_PASSWORD` in a private shell environment, then run:

```sh
./test.sh
./build.sh release
```

The release path never creates or substitutes a signing key. Debug builds use a
separate cached key. The package ID remains `lean.tvremote`.

## Publish a version

1. Update `VERSION` to a strictly increasing `major.minor.patch` (each component 0–99).
2. Update `CHANGELOG.md` and `docs/release-notes.md`. Refresh screenshots if the UI changed.
3. Run `./test.sh` and `./build.sh debug`; test on a real phone and TV for behavior changes.
4. Commit and push `main`, then create and push the matching tag:

```sh
git tag -a "v$(cat VERSION)" -m "TV Remote $(cat VERSION)"
git push origin "v$(cat VERSION)"
```

The release workflow checks the tag against `VERSION`, runs tests, restores the
private keystore to a temporary runner file, builds and verifies the release APK,
and publishes the APK, `.sha256`, and `.certificate.txt` to GitHub Releases.
The temporary keystore is removed even when a step fails. Signing secrets are never
used by pull request CI.

Verify the release assets and compare the certificate SHA-256 digest with the
previous release. Download the APK and checksum into the same directory and run
`sha256sum --check <filename>.apk.sha256`.

If a release run fails before publishing, fix the cause and rerun the failed job.
Do not move published tags or replace an already released APK; ship a new version.
