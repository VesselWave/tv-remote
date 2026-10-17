# Production reproducibility verification — 2026-10-17

**Passed:** the production-signed Wave Remote 0.6 APK and the independent
Debian/F-Droid rebuild pass signature copying. Copying the production signature
onto the F-Droid APK produces exactly the signed release's bytes. F-Droid's own
`publish --error-on-failed` also succeeds under Python 3.13 into a temporary local
repository, whose APK is byte-identical to the release. No external publication
or official hosted-buildserver acceptance is implied.

## Inputs and environment

- Source commit: `f0c6f4b76049fafd27873586ecf37e154742ea45`; version 0.6, code 10012.
- Package `me.vwave.remote`; minimum API 24, target API 36; ABI `arm64-v8a`.
- Node 24.22.0, npm 11.19.1; install `npm ci --ignore-scripts --no-audit --no-fund`.
- Node archive SHA-256:
  `cc2f601cee82d9a5a233302fe307abc600727035fd7d2c3a3571755989e95008`.
- Gradle 9.3.1, build-tools 36.0.0, NDK 27.1.12297006, CMake 3.22.1.
- Upstream runtime: Temurin 21.0.12.1+1; downloaded compilation toolchain:
  Temurin 17.0.20.1+1.
- F-Droid: fresh Debian trixie chroot, Debian JDK 21.0.12.1 runtime and
  Debian JDK 17.0.19 compilation toolchain (from bookworm).
- fdroidserver 2.4.5, Python 3.13.5 in Debian; official gradlew-fdroid revision
  `c7227d147483979bb5c408048cee3533a8814fb0`.
- Standalone apksigcopier 1.1.1 and fdroidserver's bundled copier tested with
  Python 3.13.16. The final release does not require Python 3.12.
- Production certificate SHA-256:
  `a2a5c53e76ebefe4c0c5b288904a813dc3d2a61e3802ac8066466167913ed69c`.
  Matches the local release key and the published v0.5 certificate.

Evidence directory: `/home/user/.cache/wave-fdroid-ready`. The machine-readable
manifest is `verification.json`. Upstream and F-Droid used separate checkout and
Gradle cache directories. Neither side received the other's compiled outputs.

## Procedure

The upstream checkout installed the locked dependencies with the pinned Node/npm,
then ran `./build.sh release` with the production signing environment. No signing
secrets were written to logs or included in the submission bundle. Gradle used
`--max-workers=2 --no-daemon --no-build-cache --rerun-tasks` and in-process Kotlin
compilation to limit memory use.

The Debian builder was bootstrapped with `debootstrap --variant=minbase trixie`.
The host SDK was mounted read-only. fdroidserver ran as an unprivileged builder
using `fdroid build --on-server --no-tarball -v me.vwave.remote:10012`. The recipe's
sudo commands actually ran: bookworm repository/JDK 17 setup, official Node
archive download, checksum verification and installation. fdroidserver then
removed sudo, prepared the source, scanned it and ran Gradle through the official
F-Droid wrapper. Worker count was limited to two and Kotlin compiled in process.

For local execution, the metadata changed the source transport (`Repo: /evidence/source`)
and omitted `Binaries` until the release is externally available. The local mirror
contains the exact pinned source commit. The public submission recipe retains
its real HTTPS repository and versioned release URL. The unsigned output was
compared directly to the production APK; the local publish test staged that same
APK at F-Droid's expected binary path and used the submission metadata.

Initial builds started with empty npm/Gradle download caches. Running both sides
at once exhausted available memory, so the interrupted Debian attempt was retained
as `fdroid-cold-interrupted.log` and subsequent builds ran sequentially. Final
builds reused each side's own downloaded dependencies. The final upstream build
executed 711 tasks and F-Droid executed 710 tasks.

## Signing correction

Native/path fixes made all non-signature payloads match. Preserving APK alignment
was also required. The first production signature still included obsolete v1/JAR
entries: unmodified apksigcopier under Python 3.12 passed, but Python 3.13 changed
six UTF-8 ZIP flag bytes while copying those entries. An actual F-Droid publish
dry run reproduced this failure.

`build.sh` now signs with both `--alignment-preserved` and
`--v1-signing-enabled false`. API 24+ supports v2 signatures. The final APK verifies
with v2 and v3 and contains no legacy JAR signature entries. Both sides were
rebuilt from the final committed revision. Python 3.13 standalone comparison,
byte comparison and actual F-Droid publishing then passed without patching any
comparison tool, APK payload or verification output.

## Final results

```text
57aed7aa0ec90d06b8d6400e9dd723ea0a09d35749dc368523067d5b915e302c  wave-remote-v0.6.apk
6f574fed06ce58354f212fcbd938d27a055f1eeb2794a0b17d5e7a18c0bb1f77  me.vwave.remote_10012.apk (unsigned)
57aed7aa0ec90d06b8d6400e9dd723ea0a09d35749dc368523067d5b915e302c  local F-Droid published APK
```

All 16 native libraries and all other non-signature ZIP payloads match. The signed
and unsigned APKs pass `zipalign -c -P 16 4`. The signed APK passes apksigner with
the expected production certificate. The APK scanner found no known non-free
classes or extra signing blocks. Metadata lint/format, actionlint, shell syntax,
lockfile consistency, typecheck, ESLint, nine app tests (44 assertions), and
protocol/timing/pairing/pinning/discovery/wake tests passed. The helper also rejects
a wrong expected signing fingerprint.

Repeat the key check with an independent unsigned build:

```sh
APKSIGCOPIER=apksigcopier ./tools/verify-fdroid.sh \
  dist/wave-remote-v0.6.apk me.vwave.remote_10012.apk \
  a2a5c53e76ebefe4c0c5b288904a813dc3d2a61e3802ac8066466167913ed69c
```

Use the JDK on PATH or set JAVA_HOME. SDK/build-tools default to the same settings
as build.sh. No private signing key is needed to run this verification.

## Scope and review

This proves production-signed local reproducibility across Ubuntu/Temurin and
Debian/OpenJDK environments. It does not claim official F-Droid VM/pipeline
execution or acceptance. F-Droid must still
review policy disclosures and the submission; publication must use and verify
the actual versioned release APK. Earlier debug-only experiments are historical
and are superseded by this production verification.
