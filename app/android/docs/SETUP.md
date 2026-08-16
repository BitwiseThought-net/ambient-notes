# Dev Environment Setup — AmbientNotes (Android)

## 1. Prerequisites

- **JDK 17+** — [Temurin](https://adoptium.net/) is a good free distribution.
- **Android Studio** (Koala/2024.1+ recommended) — https://developer.android.com/studio. Includes the Android
  SDK, platform tools (`adb`), and an emulator manager. Command-line-only setups can instead install the
  [Android command-line tools](https://developer.android.com/tools) directly.
- A device or emulator running **Android 8.0 (API 26)** or newer (`minSdk = 26`, chosen because
  `FOREGROUND_SERVICE_MICROPHONE` semantics and modern audio APIs assume 26+).

## 2. Get the code

```bash
git clone https://github.com/<your-org>/ambient-notes.git
cd ambient-notes
```

## 3. Bootstrap the Gradle wrapper

This repo intentionally does **not** commit `gradle/wrapper/gradle-wrapper.jar` (a binary file) — only
`gradle-wrapper.properties`, which pins the exact Gradle version (`8.7`). Generate the jar once:

```bash
./scripts/setup_dev_env.sh
```

This checks your JDK version and runs `gradle wrapper --gradle-version 8.7` for you (using a system Gradle
install if present, or prompting you to install one / open in Android Studio instead, which does this
automatically on first project sync).

If you'd rather do it by hand: open the project folder in Android Studio — it detects the missing wrapper
jar and offers to regenerate it as part of the initial Gradle sync.

## 4. Open / build

**Android Studio:** File → Open → select the `ambient-notes` folder. Let Gradle sync finish, then use the
Run button, or Build → Build Bundle(s)/APK(s) → Build APK(s).

**Command line:**

```bash
./gradlew assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (recognition logic, targets, templates)
./gradlew connectedDebugAndroidTest  # instrumented tests -- needs a connected device/emulator
./gradlew lint                   # Android Lint
```

## 5. Next steps

- [docs/BUILD.md](BUILD.md) — release builds, signing, ProGuard/R8
- [docs/SIDELOADING.md](SIDELOADING.md) — installing a build onto your own device without the Play Store
- [docs/CONFIGURATION.md](CONFIGURATION.md) — connecting recognition sources and post targets on-device
