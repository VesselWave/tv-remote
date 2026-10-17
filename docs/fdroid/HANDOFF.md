# F-Droid readiness handoff — 2026-10-17

This supersedes the earlier debug-only handoff. The current source revision is
`f0c6f4b76049fafd27873586ecf37e154742ea45`, version 0.6 / 10012. The native prefix-map
fix, alignment-preserving signing, Node-based packaging and Python 3.13 compatible
v2/v3 signing are committed. `157765d` contains the first release preparation;
`f0c6f4b` adds the final signing fix and verification helper.

The production certificate matches the published v0.5 certificate. Independent
Ubuntu/Temurin upstream and Debian/OpenJDK F-Droid builds passed exact signature
copying. The final F-Droid `publish` check uses Python 3.13 and only writes to a
temporary local repository. No external release or submission has been made.

- Current status and publication sequence: [README.md](README.md).
- Production evidence: [REPRODUCIBILITY.md](REPRODUCIBILITY.md).
- Review text: [SUBMISSION.md](SUBMISSION.md).
- Final recipe: [me.vwave.remote.yml](me.vwave.remote.yml).
- APK/checksum/certificate: `dist/wave-remote-v0.6.apk*`.
- Offline submission bundle: `dist/wave-remote-v0.6-fdroid-submission.tar.gz`.
- Full local evidence: `/home/user/.cache/wave-fdroid-ready/`.

The Debian chroot ran `fdroid build --on-server`, including the sudo provisioning
that earlier local tests skipped. It used the host SDK read-only and the official
gradlew-fdroid wrapper. No compiled output was shared between the two builds.

Earlier v1-signed APKs passed only under Python 3.12; Python 3.13 changed six ZIP
flag bytes while copying JAR entries. Explicitly disabling v1 signing fixes this
for the app's API 24+ minimum. Do not reinstate the Python 3.12 workaround or
patch copied APKs. `tools/verify-fdroid.sh` verifies the actual production signer,
signatures, alignment and byte-identical signature copying.

The user's latest build-tool instruction authorizes npm/Node for release/F-Droid
packaging. Development dependency work and app tests continue to use Bun. Builds
were authorized by "Fix till ready, don't stop". No agent delegation was used.
On a Pixel 7, the production APK installed over 0.5, kept the saved TV and showed the corrected disconnected screen. TV-facing behavior is unchanged from 0.5 and was not retested against a TV.
F-Droid's hosted pipeline and manual acceptance remain
external review steps; scanner and LG registration-data disclosures are prepared.
