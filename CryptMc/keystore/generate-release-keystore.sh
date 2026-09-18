#!/usr/bin/env bash
# Generates a real release-signing keystore for CryptMc.
# Run this yourself (don't reuse a keystore anyone else has seen the
# passwords for) and keep the output file + passwords private — losing
# either one for a keystore you've already published to Play means you
# can never update that app listing again under the same package.
set -euo pipefail
cd "$(dirname "$0")"

read -rp "Store password: " -s STORE_PASS; echo
read -rp "Key password (enter to reuse store password): " -s KEY_PASS; echo
KEY_PASS=${KEY_PASS:-$STORE_PASS}

keytool -genkeypair -v \
  -keystore cryptmc-release.keystore.jks \
  -alias cryptmc \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "CN=CryptMc, OU=Dev, O=CryptMc, L=Unknown, ST=Unknown, C=US"

echo
echo "Keystore written to keystore/cryptmc-release.keystore.jks"
echo "Now put matching values in gradle.properties (see gradle.properties.example) —"
echo "never commit the real gradle.properties or the .jks file."
