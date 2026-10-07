#!/usr/bin/env sh
# Minimal Gradle Wrapper bootstrap.
# If gradle-wrapper.jar is missing (fresh checkout), download Gradle 8.7 and run it.
# Android Studio / CI normally provide gradle-wrapper.jar; this keeps local + CI working.
set -e
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
PROPS="gradle/wrapper/gradle-wrapper.properties"
DIST_URL=$(grep distributionUrl "$PROPS" | cut -d= -f2-)
if [ -f "$WRAPPER_JAR" ]; then
  exec java -jar "$WRAPPER_JAR" "$@"
elif command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "gradle-wrapper.jar not found and no system gradle. Downloading Gradle 8.7..."
  TMP="${TMPDIR:-/tmp}/gradle-8.7-bin.zip"
  curl -L -o "$TMP" "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
  UNZIP_DIR="${TMPDIR:-/tmp}/gradle-dl"
  mkdir -p "$UNZIP_DIR" && unzip -q -o "$TMP" -d "$UNZIP_DIR"
  exec "$UNZIP_DIR/gradle-8.7/bin/gradle" "$@"
fi
