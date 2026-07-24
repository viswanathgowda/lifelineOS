#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/certs"
DEV="$ROOT/certs-dev"
mkdir -p "$OUT" "$DEV"
PASS=changeit
rm -f "$OUT"/* "$DEV"/*

keytool -genkeypair -alias lifeline-server -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=localhost,O=lifelineOS,C=US" \
  -keystore "$OUT/server-keystore.p12" -storetype PKCS12 -storepass "$PASS" -keypass "$PASS" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt

keytool -genkeypair -alias lifeline-ui-client -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=lifeline-ui,O=lifelineOS,C=US" \
  -keystore "$DEV/client-keystore.p12" -storetype PKCS12 -storepass "$PASS" -keypass "$PASS" -noprompt

keytool -exportcert -alias lifeline-server -keystore "$OUT/server-keystore.p12" -storepass "$PASS" \
  -file "$DEV/server.cer" -rfc
keytool -exportcert -alias lifeline-ui-client -keystore "$DEV/client-keystore.p12" -storepass "$PASS" \
  -file "$DEV/client.cer" -rfc

keytool -importcert -alias lifeline-ui-client -file "$DEV/client.cer" \
  -keystore "$OUT/truststore.p12" -storetype PKCS12 -storepass "$PASS" -noprompt
keytool -importcert -alias lifeline-server -file "$DEV/server.cer" \
  -keystore "$DEV/client-truststore.p12" -storetype PKCS12 -storepass "$PASS" -noprompt

echo "Server material: $OUT"
echo "Client material: $DEV"
echo "Password: $PASS"
