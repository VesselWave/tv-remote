# F-Droid

[me.vwave.remote.yml](me.vwave.remote.yml) is the fdroiddata recipe. F-Droid builds
the app from source and publishes the APK from GitHub Releases only when its own
build matches it byte for byte, so the release build is kept reproducible:

- Release packaging uses the Node.js version in `.node-version` and `npm ci` with
  `package-lock.json`, as the recipe does.
- Expo native modules are built from source instead of their bundled AARs.
- Native builds map checkout and Gradle cache paths to fixed prefixes, and link
  without a build ID.
- Signing preserves ZIP alignment and uses v2/v3 signatures only (the minimum is
  Android 7.0, API 24). Leaving out v1/JAR entries also keeps F-Droid's signature
  copying byte-exact under Python 3.13.

To check a release against an unsigned rebuild, for example F-Droid's
`me.vwave.remote_<versionCode>.apk`:

```sh
./tools/verify-fdroid.sh wave-remote-v0.6.1.apk me.vwave.remote_10013.apk \
  a2a5c53e76ebefe4c0c5b288904a813dc3d2a61e3802ac8066466167913ed69c
```

It checks the release certificate, the signatures and ZIP alignment, then copies
the release signature onto the rebuild and compares the result byte for byte.
It needs `apksigcopier` and the Android build-tools, not the signing key.
