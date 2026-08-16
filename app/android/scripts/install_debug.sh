#!/usr/bin/env bash
# Builds a debug APK and installs it on a connected device/emulator via adb.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found on PATH. Install Android SDK Platform-Tools and add it to PATH." >&2
  exit 1
fi

DEVICE_COUNT=$(adb devices | tail -n +2 | grep -c "device$" || true)
if [ "${DEVICE_COUNT}" -eq 0 ]; then
  echo "No device/emulator detected. Connect a device with USB debugging enabled, or start an emulator." >&2
  exit 1
fi

./gradlew assembleDebug
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n1)
echo "Installing ${APK_PATH}..."
adb install -r "${APK_PATH}"
echo "Installed. Launch AmbientNotes from the device's app drawer."
