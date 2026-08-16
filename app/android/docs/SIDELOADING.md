# Sideloading AmbientNotes

AmbientNotes isn't published on the Play Store - it's installed directly ("sideloaded") from a built APK.

## Option A: from a CI build (no local build environment needed)

1. Open the relevant pull request or commit's checks on GitHub, find the **AmbientNotes Android CI**
   workflow run.
2. Under **Artifacts**, download `app-debug`.
3. Unzip it to get `app-debug.apk`, then follow "Installing the APK" below.

## Option B: build it yourself

```bash
./scripts/install_debug.sh
```

This builds a debug APK and installs it directly via `adb` onto a connected device or running emulator (see
[docs/SETUP.md](SETUP.md) if you haven't set up `adb`/the SDK yet). Or build without installing:

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Installing the APK

**Via adb (recommended, works for debug or release APKs):**

```bash
adb install -r app-debug.apk
```

**Directly on-device (no computer needed once you have the APK file, e.g. downloaded from a CI artifact on
your phone's browser):**

1. Settings → Apps → Special access → Install unknown apps → enable for your browser/file manager.
2. Open the downloaded `.apk` file and confirm the install prompt.
3. (You can re-disable "install unknown apps" afterward if you'd prefer it off by default.)

## First launch

1. Grant the microphone permission when prompted (needed for ambient listening).
2. Grant the notifications permission (Android 13+) so the "AmbientNotes is listening" foreground-service
   notification can show - Android requires this for any persistent background service.
3. Go to **Settings** in-app to connect at least one recognition source and, optionally, one or more post
   targets - see [docs/CONFIGURATION.md](CONFIGURATION.md).
4. Consider exempting AmbientNotes from battery optimization (Settings → Apps → AmbientNotes → Battery →
   Unrestricted) if you want listening to survive aggressive OEM battery managers (Samsung, Xiaomi, etc. are
   known to kill background services more aggressively than stock Android).

## Updating

Sideloaded apps don't auto-update. Repeat "Option A" or "Option B" with a newer build and `adb install -r`
(the `-r` flag reinstalls over the existing app, preserving your settings and history).
