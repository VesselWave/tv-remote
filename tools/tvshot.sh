#!/usr/bin/env bash
# Captures the TV screen to $3 (default /tmp/tv.jpg) via ssap://tv/executeOneShot.
set -euo pipefail
HERE=$(dirname "$0")
uri=$(python3 "$HERE/ssap_probe.py" "$1" "$2" ssap://tv/executeOneShot | sed -n 's/.*"imageUri": "\([^"]*\)".*/\1/p')
curl -sk -m 10 -o "${3:-/tmp/tv.jpg}" "$uri"
