#!/usr/bin/env bash
# Builds the original framework-only Java app (v0.1 UI) for reference. No Gradle or third-party
# runtime dependencies. The shipped app is the Expo build in ../build.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/opt/android-sdk}}
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
JAR="$SDK/platforms/android-35/android.jar"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
VERSION_NAME=$(cat VERSION)
if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo 'VERSION must be major.minor or major.minor.patch' >&2; exit 1
fi
VERSION_CODE=$(cat VERSION_CODE)
if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]{0,8}$ ]]; then
    echo 'VERSION_CODE must be a positive integer of at most nine digits' >&2; exit 1
fi
mode=${1:-debug}
link_flags=()
case "$mode" in
    debug)
        link_flags+=(--debug-mode)
        KS="${DEBUG_KEYSTORE:-${XDG_CACHE_HOME:-$HOME/.cache}/tv-remote/debug.keystore}"
        mkdir -p "$(dirname "$KS")"
        export TVREMOTE_STORE_PASSWORD=android TVREMOTE_KEY_PASSWORD=android
        KEY_ALIAS=androiddebugkey
        if [[ ! -f "$KS" ]]; then
            keytool -genkeypair -keystore "$KS" -storepass:env TVREMOTE_STORE_PASSWORD \
                -keypass:env TVREMOTE_KEY_PASSWORD -alias "$KEY_ALIAS" -keyalg RSA \
                -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1
        fi
        ;;
    release)
        : "${RELEASE_KEYSTORE:?Set RELEASE_KEYSTORE to your private signing keystore}"
        : "${TVREMOTE_STORE_PASSWORD:?Set TVREMOTE_STORE_PASSWORD}"
        : "${TVREMOTE_KEY_PASSWORD:?Set TVREMOTE_KEY_PASSWORD}"
        : "${RELEASE_KEY_ALIAS:?Set RELEASE_KEY_ALIAS}"
        KS="$RELEASE_KEYSTORE"
        KEY_ALIAS="$RELEASE_KEY_ALIAS"
        ;;
    *) echo 'Usage: tools/build-legacy.sh [debug|release]' >&2; exit 1 ;;
esac
for tool in aapt2 d8 zipalign apksigner; do
    [[ -x "$BT/$tool" ]] || { echo "Missing $BT/$tool; install Android SDK build-tools 35.0.0" >&2; exit 1; }
done
[[ -f "$JAR" ]] || { echo "Missing $JAR; install platforms;android-35" >&2; exit 1; }
[[ -f "$KS" ]] || { echo "Signing keystore not found: $KS" >&2; exit 1; }
rm -rf build
mkdir -p build/classes build/dex build/gen dist
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen \
    --min-sdk-version 28 --target-sdk-version 35 --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" "${link_flags[@]}" build/res.zip
find src build/gen -name '*.java' | sort > build/sources.txt
javac -nowarn --release 11 -classpath "$JAR" -d build/classes @build/sources.txt
mapfile -t classes < <(find build/classes -name '*.class' | sort)
"$BT/d8" --release --min-api 28 --lib "$JAR" --output build/dex "${classes[@]}"
python3 - <<'PYZIP'
import zipfile
with zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
    apk.write('build/dex/classes.dex', 'classes.dex')
PYZIP
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
suffix=""
if [[ "$mode" == debug ]]; then suffix="-debug"; fi
APK="dist/tv-remote-legacy-v$VERSION_NAME$suffix.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:TVREMOTE_STORE_PASSWORD --key-pass env:TVREMOTE_KEY_PASSWORD \
    --out "$APK" build/aligned.apk
"$BT/apksigner" verify --verbose --print-certs "$APK" > "$APK.certificate.txt"
"$BT/zipalign" -c -p 4 "$APK"
(cd dist && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256")
ls -lh "$APK"
