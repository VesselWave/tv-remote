# F-Droid

`me.vwave.remote.yml` is the fdroiddata recipe, ready to submit as
`metadata/me.vwave.remote.yml`. It passes `fdroid lint` and `fdroid rewritemeta`, and
`fdroid build` runs it cleanly: the source scanner passes and Gradle builds the APK.

## Reproducible builds

The recipe sets `Binaries` and `AllowedAPKSigningKeys`, so F-Droid rebuilds each tag,
compares the result with the APK on GitHub Releases, and publishes ours, with our signature,
only if the two match. Users can then switch between F-Droid and GitHub builds without
reinstalling. F-Droid can't adopt our signature after shipping its own, so this has to work
from the first submitted version.

What keeps the two builds identical:

- Both compile with a JDK 17 toolchain, as React Native's Gradle plugin asks. Gradle
  downloads one for `./build.sh`; the recipe installs Debian's `openjdk-17-jdk-headless`.
  Don't patch the toolchain to 21 in the recipe: that changes the bytecode.
- `plugins/with-wave-remote.cjs` links native libraries without a GNU build ID, which
  would otherwise hash the build paths.
- `npm ci` installs from `package-lock.json`, which `tools/npm-lock.py --check` keeps
  identical to `bun.lock`.
- Same NDK (27.1.12297006) and `arm64-v8a` only.

To check a release locally, run the recipe with `fdroid build` and compare:
`apksigcopier compare wave-remote-v<version>.apk --unsigned <fdroid unsigned apk>`.

## Submitting

1. Point `commit` at the full hash of the release tag (`git rev-parse v<version>^{commit}`)
   and set `versionName`, `versionCode`, `CurrentVersion` and `CurrentVersionCode` to match.
   The release must include the reproducibility settings above (0.6 or later).
2. Fork https://gitlab.com/fdroid/fdroiddata, add the file, and open a merge request titled
   "New app: Wave Remote" using the "App inclusion" template. Keep the description short and
   factual. If asked about AI assistance, answer plainly.
3. Make the pipeline pass and answer reviewer questions. `MaintainerNotes` already covers
   the build, the cleartext permission, and the LG registration manifest (see NOTICE.md).

After inclusion, F-Droid picks up new tags by itself (`UpdateCheckMode: Tags`). Each tag's
`VERSION` and `VERSION_CODE` must be updated, as `docs/RELEASING.md` already requires.

IzzyOnDroid isn't an option: its inclusion policy rejects apps whose code was written
in part with generative AI tools.
