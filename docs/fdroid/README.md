# F-Droid submission readiness — 0.6

The local release and submission artifacts are prepared for F-Droid inclusion
review. The production-signed APK passes byte-identical signature copying against
an independent Debian/F-Droid rebuild, including F-Droid's own publishing check
under Python 3.13. Nothing has been pushed, released or submitted externally.
F-Droid's maintainers decide inclusion after their pipeline and manual review.

## Prepared artifacts

- Version **0.6**, code **10012**, application ID `me.vwave.remote`.
- Release source: `f0c6f4b76049fafd27873586ecf37e154742ea45`. The recipe pins this full commit.
- APK: `dist/wave-remote-v0.6.apk`, with checksum and certificate sidecars.
- [Submission recipe](me.vwave.remote.yml), with active `Binaries` and the verified
  production `AllowedAPKSigningKeys`. The certificate matches published v0.5.
- [Submission description](SUBMISSION.md), Fastlane metadata, icon, screenshots,
  MIT license and [protocol provenance](../../NOTICE.md).
- [Verification evidence](REPRODUCIBILITY.md) and
  [repeatable signature check](../../tools/verify-fdroid.sh).
- Offline bundle: `dist/wave-remote-v0.6-fdroid-submission.tar.gz`.

## Build toolchain

Release packaging uses Node.js **24.22.0**, npm's locked dependency installation,
Expo's installed CLI and Gradle. `build.sh` never invokes Bun. CI runs Bun-based
checks in a separate job; its packaging job installs Node and runs `npm ci`.
F-Droid downloads the same official Node release and verifies its SHA-256.
Development continues to use Bun.

Both builds compile Expo native modules from source and remove bundled Expo
`local-maven-repo` AARs. Native checkout/cache paths are normalized. Final signing
preserves ZIP alignment and disables unnecessary v1/JAR signatures; Android 7+
supports v2/v3. This removes the Python 3.13 ZIP-flag failure from earlier tests.

## Checks completed

- Typecheck, ESLint, nine app tests and protocol/timing/pairing/discovery tests.
- npm/Bun lockfile consistency, shell syntax, workflow lint and metadata lint/format.
- Production APK signature, signer, version/code, SHA-256 and 16 KiB ZIP alignment.
- F-Droid source scan, unsigned build and APK scan for known non-free classes and
  additional signing blocks.
- Independent builds with different checkout/cache roots and different JDK vendors.
- Signature copying and byte comparison under Python 3.13, plus successful
  `fdroid publish --error-on-failed` into a temporary local repository.
- The verification script rejects a mismatched production signer.

The build was tested in a fresh Debian trixie chroot with `--on-server`, including
actual execution of the recipe's sudo provisioning. It was not run on F-Droid's
hosted VM. The SDK was supplied from the host. Initial dependency/Gradle downloads
started from empty caches; the final rebuild reused each side's own downloaded
cache after the signing fix. Compiled outputs were not shared between builds.
On a Pixel 7, the production APK installed over 0.5, kept the saved TV and showed the corrected disconnected screen. TV-facing behavior is unchanged from 0.5 and was not retested against a TV.
Existing connected-state screenshots remain representative; they were not relabeled
as new captures.

## Publication and review

1. Push the release source and documentation, then publish **v0.6** from the pinned
   source commit. Do not move existing release tags or replace released APKs.
2. Check the actually published APK's certificate and checksum. If CI produces a
   different APK from the verified artifact, repeat `tools/verify-fdroid.sh` against
   that APK and the independent rebuild before proceeding with developer-signed
   F-Droid publication. Keep the actual release under the recipe's `Binaries` URL.
3. Copy the recipe into fdroiddata as `metadata/me.vwave.remote.yml` and use
   [SUBMISSION.md](SUBMISSION.md) for the inclusion request. Resolve its pipeline
   and reviewer findings. These are external publication/review steps, not completed
   actions in this checkout.

The scanner exemptions and LG registration data are explicitly disclosed in
MaintainerNotes and the submission description. They remain subject to normal
manual review. No blanket eligibility or legal approval is claimed. Current
[F-Droid policy](https://f-droid.org/en/docs/Inclusion_Policy/) permits trusted
Maven dependencies, official Hermes binaries and current Node.js tooling; the
[React Native template](https://gitlab.com/fdroid/fdroiddata/-/blob/master/templates/build-react-native.yml)
uses npm/Node. [Developer-signed publication](https://f-droid.org/en/docs/Reproducible_Builds/)
requires the rebuilt APK to match. Sources checked on 2026-10-17.
