#!/usr/bin/env bash
# Verify the production signer and exact signature-copy reproducibility.
# Usage: ./tools/verify-fdroid.sh signed.apk fdroid-unsigned.apk expected-certificate-sha256
set -euo pipefail
if [[ $# != 3 ]]; then
    echo 'Usage: ./tools/verify-fdroid.sh signed.apk fdroid-unsigned.apk expected-certificate-sha256' >&2
    exit 1
fi
signed=$1
unsigned=$2
expected=$3
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Expected certificate SHA-256 must be 64 lowercase hex digits' >&2; exit 1; }
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/opt/android-sdk}}
bt="$sdk/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
copier=${APKSIGCOPIER:-apksigcopier}
export PATH="$bt:$PATH"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
"$bt/apksigner" verify --verbose --print-certs "$signed" > "$temporary/certificate.txt"
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$temporary/certificate.txt")
[[ "$actual" == "$expected" ]] || { echo 'Production signing certificate does not match' >&2; exit 1; }
grep -q '^Number of signers: 1$' "$temporary/certificate.txt"
"$bt/zipalign" -c -P 16 4 "$signed"
"$bt/zipalign" -c -P 16 4 "$unsigned"
"$copier" compare "$signed" --unsigned "$unsigned"
"$copier" copy "$signed" "$unsigned" "$temporary/reconstructed.apk"
cmp "$signed" "$temporary/reconstructed.apk"
echo 'Verified production certificate, APK signatures, ZIP alignment, and byte-identical signature copying.'
