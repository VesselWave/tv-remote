# Project and protocol provenance

Wave Remote is an independent project by VesselWave. It is not affiliated with,
sponsored by, or endorsed by LG Electronics. LG, LG ThinQ, and webOS are trademarks
of their respective owners.

The app and test tools were extracted from VesselWave's private `rea-stuff`
research workspace, commit `2b50a50`. The Expo app (0.2) followed from the same
workspace, commit `2dd8635`, which now includes this repository as a submodule. The local protocol was documented through
interoperability research on ThinQ 5.2.12500. Public implementation notes are in
[docs/PROTOCOL.md](docs/PROTOCOL.md). No ThinQ APK, decompiled application, or
research dump is distributed here.

`Tv.Manifest` reproduces, field for field, the registration manifest ThinQ sends
when it pairs with a TV: LG's app and vendor IDs, a list of permission names, and
the RSA signature LG's signing tool made over them. The TV checks this manifest at
registration, which is why the pairing prompt can say **LG Mobile**. It is functional
interoperability data: a fixed value any client must send, holding no LG program
code, and the signature is machine-generated rather than written. It is not the APK
signing key, and nothing in it is secret. MIT-licensed webOS clients such as
[PyWebOSTV](https://github.com/supersaiyanmode/PyWebOSTV) and
[LGWebOSRemote](https://github.com/klattimer/LGWebOSRemote) have long shipped LG's
earlier "LG Remote App" manifest in the same way. VesselWave didn't author these
values, so the MIT license doesn't cover them, and it grants no rights to LG
trademarks.

The test runner downloads JSON-java 20240303 from Maven Central. It is used only
on the JVM, is not bundled into the APK, and is licensed by its authors under
[the JSON-java public domain notice](https://github.com/stleary/JSON-java/blob/20240303/LICENSE).
Android supplies `org.json` at runtime.

Since 0.2 the APK bundles open-source runtime libraries under their own licenses,
including Expo, React Native and React (MIT), React Native Paper (MIT),
`@expo/vector-icons` (MIT) with Material Design Icons by Pictogrammers (Apache-2.0),
and their dependencies. `bun.lock` and `package-lock.json` list the exact versions.

The website (`site/`, not part of the app) includes a Latin subset of the Archivo
font by Omnibus-Type, under the SIL Open Font License 1.1.
