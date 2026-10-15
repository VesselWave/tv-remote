# Project and protocol provenance

TV Remote is an independent project by VesselWave. It is not affiliated with,
sponsored by, or endorsed by LG Electronics. LG, LG ThinQ, and webOS are trademarks
of their respective owners.

The app and test tools were extracted from VesselWave's private `rea-stuff`
research workspace, commit `2b50a50`. The local protocol was documented through
interoperability research on ThinQ 5.2.12500. Public implementation notes are in
[docs/PROTOCOL.md](docs/PROTOCOL.md). No ThinQ APK, decompiled application, or
research dump is distributed here.

`Tv.Manifest` contains the signed registration manifest and public signature used
by LG's local TV protocol. The on-TV pairing prompt can therefore identify the
client as **LG Mobile**. This is protocol compatibility data, not the APK signing
key. The MIT license applies to the project's original code and assets; it does
not grant rights to LG trademarks or third-party protocol material.

The test runner downloads JSON-java 20240303 from Maven Central. It is used only
on the JVM, is not bundled into the APK, and is licensed by its authors under
[the JSON-java public domain notice](https://github.com/stleary/JSON-java/blob/20240303/LICENSE).
Android supplies `org.json` at runtime.
