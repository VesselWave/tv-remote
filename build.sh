#!/usr/bin/env bash
# Builds the Expo app into a signed, verified APK: ./build.sh [debug|release]
# Expo generates android/ with Gradle; apksigner then applies the debug or release identity.
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/opt/android-sdk}}
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
VERSION_NAME=$(cat VERSION)
if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo 'VERSION must be major.minor or major.minor.patch' >&2; exit 1
fi
VERSION_CODE=$(cat VERSION_CODE)
if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]{0,8}$ ]]; then
    echo 'VERSION_CODE must be a positive integer of at most nine digits' >&2; exit 1
fi
# Comma-separated Android ABIs. arm64-v8a covers current phones and keeps the APK small.
ABIS=${ABIS:-arm64-v8a}
mode=${1:-debug}
case "$mode" in
    debug)
        KS="${DEBUG_KEYSTORE:-${XDG_CACHE_HOME:-$HOME/.cache}/wave-remote/debug.keystore}"
        mkdir -p "$(dirname "$KS")"
        export WAVEREMOTE_STORE_PASSWORD=android WAVEREMOTE_KEY_PASSWORD=android
        KEY_ALIAS=androiddebugkey
        if [[ ! -f "$KS" ]]; then
            keytool -genkeypair -keystore "$KS" -storepass:env WAVEREMOTE_STORE_PASSWORD \
                -keypass:env WAVEREMOTE_KEY_PASSWORD -alias "$KEY_ALIAS" -keyalg RSA \
                -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1
        fi
        ;;
    release)
        : "${RELEASE_KEYSTORE:?Set RELEASE_KEYSTORE to your private signing keystore}"
        : "${WAVEREMOTE_STORE_PASSWORD:?Set WAVEREMOTE_STORE_PASSWORD}"
        : "${WAVEREMOTE_KEY_PASSWORD:?Set WAVEREMOTE_KEY_PASSWORD}"
        : "${RELEASE_KEY_ALIAS:?Set RELEASE_KEY_ALIAS}"
        KS="$RELEASE_KEYSTORE"
        KEY_ALIAS="$RELEASE_KEY_ALIAS"
        ;;
    *) echo 'Usage: ./build.sh [debug|release]' >&2; exit 1 ;;
esac
for tool in zipalign apksigner; do
    [[ -x "$BT/$tool" ]] || { echo "Missing $BT/$tool; install Android SDK build-tools 36.0.0" >&2; exit 1; }
done
command -v node >/dev/null || { echo 'Node.js is required to build the app' >&2; exit 1; }
[[ -f node_modules/expo/bin/cli ]] || { echo 'Install the locked dependencies first; see README.md' >&2; exit 1; }
[[ -f "$KS" ]] || { echo "Signing keystore not found: $KS" >&2; exit 1; }

# Match F-Droid: compile Expo native modules from source rather than bundled AARs.
find node_modules -type d -name local-maven-repo -prune -exec rm -rf {} +

# android/ is generated; start clean so a stale copy can't leak old modules or settings into the APK.
rm -rf android
node node_modules/expo/bin/cli prebuild --platform android --no-install
# GRADLE_FLAGS passes extra Gradle options, such as --max-workers=2 on machines with little memory.
# shellcheck disable=SC2086
(cd android && ./gradlew assembleRelease -PreactNativeArchitectures="$ABIS" --console=plain ${GRADLE_FLAGS:-})

UNSIGNED=android/app/build/outputs/apk/release/app-release.apk
suffix=""
if [[ "$mode" == debug ]]; then suffix="-debug"; fi
mkdir -p dist
APK="dist/wave-remote-v$VERSION_NAME$suffix.apk"
"$BT/zipalign" -c -P 16 4 "$UNSIGNED"
# Keep Gradle's verified ZIP alignment: newer apksigner otherwise rewrites its
# padding, preventing F-Droid's apksigcopier from reproducing the signed APK.
# Android 7+ supports v2 signatures. Omit obsolete v1/JAR entries, whose ZIP flags
# Python 3.13 rewrites during signature copying in current F-Droid tooling.
"$BT/apksigner" sign --alignment-preserved --v1-signing-enabled false --ks "$KS" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:WAVEREMOTE_STORE_PASSWORD --key-pass env:WAVEREMOTE_KEY_PASSWORD \
    --out "$APK" "$UNSIGNED"
rm -f "$APK.idsig"
"$BT/apksigner" verify --verbose --print-certs "$APK" > "$APK.certificate.txt"
(cd dist && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256")
ls -lh "$APK"
