# AmbientNotes Project

Two repositories, delivered together as a starting point:

- **app/android** - the Android app (Kotlin, Jetpack Compose). See its own README.md.
- **service** - the optional self-hosted Python recognition backend
  (FastAPI + Docker Compose). See its own README.md. This one has been executed and
  tested in the build environment: 38 tests passing, 95% line coverage, clean lint.

**ambient-notes** (Android/Kotlin): fully implemented as idiomatic, consistent Kotlin following the
architecture described in its README, including a real unit test suite (orchestrator, providers, template
engine, webhook/target posting, settings serialization). **this code has not been compiled or run**. Before treating it
as production-ready:

1. Run `./scripts/setup_dev_env.sh` and `./gradlew testDebugUnitTest` yourself, or open in Android Studio and
   let it sync - this will surface any import typos or API-signature mismatches (dependency versions were
   chosen to be mutually compatible as of early 2026, but library APIs do shift).
2. The Settings screen (`ui/screens/SettingsScreen.kt`) is a deliberately minimal placeholder listing
   provider/target names - the actual credential-entry forms per provider/target aren't built out yet. The
   backing logic (`SettingsRepository`, all providers/targets) is complete and tested; wiring a form to it
   is mechanical UI work described in `docs/CONFIGURATION.md` and `CONTRIBUTING.md`.
3. `keystore.properties` / release signing is documented but not configured - you'll need to set that up
   before a signed release build (see `ambient-notes/docs/BUILD.md`).
4. ShazamKit has no public Android SDK as of this writing - it's a documented, honest stub, not a working
   integration (see `docs/CONFIGURATION.md#shazamkit`).
5. Social OAuth targets (X/Twitter, Threads, Facebook, Reddit, LinkedIn, Tumblr) expect *you* to obtain an
   access token from each platform's own developer portal - the app doesn't implement each platform's OAuth
   consent flow. Documented per-platform in `ambient-notes/docs/CONFIGURATION.md`.

## Test coverage target

The service hits ~95% and enforces a 90% floor in CI. The Android app has substantial unit test coverage of
its testable business logic (providers, targets, template engine, orchestration, settings serialization),
but literal 100% coverage including Compose UI and the foreground service's Android-API-dependent code isn't
realistic without a device/emulator loop I don't have here - those paths are covered by the instrumented
test stub and are natural next additions once you're running this on real hardware/CI.
