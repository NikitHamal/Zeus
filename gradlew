#!/usr/bin/env sh
# Minimal gradlew stub - real wrapper will be fetched in CI
set -e
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "Downloading gradle-wrapper.jar..."
  mkdir -p gradle/wrapper
  curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar
fi
exec java -jar gradle/wrapper/gradle-wrapper.jar "$@"
