# Simple self-signed mTLS material for local lifelineOS development
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Out = Join-Path $Root "src\main\resources\certs"
$DevOut = Join-Path $Root "certs-dev"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
New-Item -ItemType Directory -Force -Path $DevOut | Out-Null
$Pass = "changeit"

Remove-Item "$Out\*" -Force -ErrorAction SilentlyContinue
Remove-Item "$DevOut\*" -Force -ErrorAction SilentlyContinue

Write-Host "Creating server keystore..."
& keytool -genkeypair -alias lifeline-server -keyalg RSA -keysize 2048 -validity 3650 `
  -dname "CN=localhost,O=lifelineOS,C=US" `
  -keystore "$Out\server-keystore.p12" -storetype PKCS12 -storepass $Pass -keypass $Pass `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt

Write-Host "Creating client keystore..."
& keytool -genkeypair -alias lifeline-ui-client -keyalg RSA -keysize 2048 -validity 3650 `
  -dname "CN=lifeline-ui,O=lifelineOS,C=US" `
  -keystore "$DevOut\client-keystore.p12" -storetype PKCS12 -storepass $Pass -keypass $Pass -noprompt

Write-Host "Exporting certificates..."
& keytool -exportcert -alias lifeline-server -keystore "$Out\server-keystore.p12" -storepass $Pass `
  -file "$DevOut\server.cer" -rfc
& keytool -exportcert -alias lifeline-ui-client -keystore "$DevOut\client-keystore.p12" -storepass $Pass `
  -file "$DevOut\client.cer" -rfc

Write-Host "Building server truststore (trusts client)..."
& keytool -importcert -alias lifeline-ui-client -file "$DevOut\client.cer" `
  -keystore "$Out\truststore.p12" -storetype PKCS12 -storepass $Pass -noprompt

Write-Host "Building client truststore (trusts server)..."
& keytool -importcert -alias lifeline-server -file "$DevOut\server.cer" `
  -keystore "$DevOut\client-truststore.p12" -storetype PKCS12 -storepass $Pass -noprompt

Write-Host ""
Write-Host "Server keystore : $Out\server-keystore.p12"
Write-Host "Server truststore: $Out\truststore.p12"
Write-Host "Client keystore  : $DevOut\client-keystore.p12"
Write-Host "Client truststore: $DevOut\client-truststore.p12"
Write-Host "Password         : $Pass"
