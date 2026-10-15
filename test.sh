#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
mkdir -p .cache out/tests
JSON=.cache/json-20240303.jar
JSON_SHA=3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed
if [[ ! -f "$JSON" ]]; then
    curl --fail --location --silent --show-error \
        https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JSON"
fi
echo "$JSON_SHA  $JSON" | sha256sum --check --status || {
    echo 'JSON-java checksum mismatch; remove .cache/json-20240303.jar and retry.' >&2; exit 1;
}
find tests/stubs tests/test -name '*.java' | sort > out/test-sources.txt
javac --release 11 -cp "$JSON" -d out/tests @out/test-sources.txt \
    src/lean/tvremote/{HeldKey,Tv,WebSocket,Prefs,Net}.java
java -cp "out/tests:$JSON" lean.tvremote.HeldKeyTest
python3 tests/run_protocol.py
