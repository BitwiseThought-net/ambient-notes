#!/usr/bin/env bash
# Bootstraps a local dev environment: checks for JDK 17+, generates the
# Gradle wrapper jar (intentionally not committed -- see docs/BUILD.md),
# and does a first dependency sync.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install a JDK 17+ (e.g. Temurin: https://adoptium.net/) and re-run this script." >&2
  exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n1 | grep -oE '"[0-9]+' | tr -d '"')
if [ "${JAVA_VERSION}" -lt 17 ]; then
  echo "Java 17+ required, found major version ${JAVA_VERSION}." >&2
  exit 1
fi

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  if command -v gradle >/dev/null 2>&1; then
    echo "Generating gradle-wrapper.jar via system Gradle..."
    gradle wrapper --gradle-version 8.7
  else
    echo "System 'gradle' not found and gradle-wrapper.jar is missing." >&2
    echo "Either install Gradle (https://gradle.org/install/) and re-run this script," >&2
    echo "or open this project in Android Studio, which will offer to generate the wrapper automatically." >&2
    exit 1
  fi
fi

chmod +x ./gradlew
echo "Running an initial dependency sync (this can take a while the first time)..."
./gradlew help

echo
echo "Dev environment ready. Next steps:"
echo "  ./gradlew testDebugUnitTest   # run unit tests"
echo "  ./gradlew assembleDebug       # build a debug APK"
echo "  ./scripts/install_debug.sh    # build + sideload onto a connected device"
