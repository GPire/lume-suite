#!/usr/bin/env bash
# Gets the OpenSSL release Ambient Cast is built against (sources are not stored in this repository: 140 MB, mostly tests).
# Run it once before the first build; tools/build-openssl.sh then compiles it for Android.
set -euo pipefail
cd "$(dirname "$0")/.."
TAG=openssl-3.5.9
if [[ -f third_party/openssl/Configure ]]; then echo "OpenSSL already in third_party/openssl"; exit 0; fi
git clone --depth 1 --branch "$TAG" https://github.com/openssl/openssl.git third_party/openssl
echo "OpenSSL $TAG ready. Now: tools/build-openssl.sh (Gradle also runs it for you)."
