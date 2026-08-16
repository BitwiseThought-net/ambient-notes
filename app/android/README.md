# AmbientNotes

An Android app that listens in the background for music playing around you, identifies it, and (optionally)
logs/shares what it heard.

- Choose your recognition source: **ACRCloud**, **AudD**, **ShazamKit** (Android SDK not yet available from
  Apple -- see [docs/CONFIGURATION.md](docs/CONFIGURATION.md#shazamkit)), or your own
  [**self-hosted AmbientNotesService**](../ambient-notes-service) instance -- all bring-your-own-account,
  nothing shared or bundled.
- Send identified songs to any number of **post targets**: a generic webhook (JSON/XML/SOAP/plain text, your
  own template), or Mastodon, Bluesky, X/Twitter, Threads, Facebook, Reddit, LinkedIn, or Tumblr.
- Everything is logged locally too, so you get a private history even with zero targets configured.

## Quick start

```bash
git clone https://github.com/<your-org>/ambient-notes.git
cd ambient-notes
./scripts/setup_dev_env.sh      # generates the Gradle wrapper jar, checks your JDK
./gradlew assembleDebug         # or open in Android Studio and hit Run
./scripts/install_debug.sh      # build + adb install onto a connected device
```

First launch: grant microphone + notification permissions, then go to **Settings** to connect a recognition
source and (optionally) any post targets.

## Documentation

| Doc | Covers |
|---|---|
| [docs/SETUP.md](docs/SETUP.md) | Dev environment: JDK, Android Studio, Gradle wrapper |
| [docs/BUILD.md](docs/BUILD.md) | Debug/release builds, signing, CI |
| [docs/SIDELOADING.md](docs/SIDELOADING.md) | Installing a build onto your own device |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Setting up every recognition source and post target |
| [docs/SECURITY.md](docs/SECURITY.md) | Permissions, credential storage, network behavior |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Running tests, project structure, adding a new provider/target |

## Architecture at a glance

```
audio/AudioCaptureService   -- foreground service; records short PCM samples on an interval
recognition/                -- RecognitionProvider interface + ACRCloud/AudD/ShazamKit/self-hosted impls
                                RecognitionOrchestrator walks the user's enabled providers, first confident match wins
targets/                    -- PostTarget interface + Webhook/Mastodon/Bluesky/OAuth-social impls
                                TemplateEngine renders user templates; TargetPostingCoordinator fans out, isolates failures
data/                       -- Room (song history) + DataStore (settings/credentials)
ui/                         -- Jetpack Compose screens (Home, Settings, History)
```

Adding a new recognition source or post target is meant to be mechanical: implement the interface, register
it in the corresponding factory, done -- see [CONTRIBUTING.md](CONTRIBUTING.md).

## Testing

```bash
./gradlew testDebugUnitTest            # fast JVM tests: providers, targets, template engine, settings serialization
./gradlew connectedDebugAndroidTest    # instrumented tests (device/emulator required)
./gradlew jacocoTestReport             # coverage report
```

Runs automatically on every pull request via [.github/workflows/android-ci.yml](.github/workflows/android-ci.yml).

## License

MIT - see [LICENSE](LICENSE).
