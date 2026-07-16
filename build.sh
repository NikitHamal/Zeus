#!/bin/bash
set -e
if [ ! -f zeus-release.keystore ]; then
  keytool -genkey -v -keystore zeus-release.keystore -alias zeus -keyalg RSA -keysize 2048 -validity 10000 -storepass zeus123 -keypass zeus123 -dname "CN=Zeus, O=Zeus, C=NP"
fi
echo "storeFile=zeus-release.keystore
storePassword=zeus123
keyAlias=zeus
keyPassword=zeus123" > keystore.properties
./gradlew assembleRelease
SHORT=$(git rev-parse --short HEAD 2>/dev/null || echo "local")
cp app/build/outputs/apk/release/*.apk ./Zeus-${SHORT}.apk
echo "Built Zeus-${SHORT}.apk"
