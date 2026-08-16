# Contributing to AmbientNotes

## Project layout

See the README's "Architecture at a glance" section.

## Running checks locally (mirrors CI)

```bash
./gradlew lint
./gradlew testDebugUnitTest jacocoTestReport
./gradlew connectedDebugAndroidTest   # requires a connected device/emulator
```

## Adding a recognition source

1. Implement `recognition/RecognitionProvider` (see `recognition/providers/AudDProvider.kt` for a compact
   example, or `AcrCloudProvider.kt` for one with request signing).
2. `recognize()` must return `RecognitionResult.noMatch(id)` for "no match" -- never throw for that case.
   Reserve exceptions (`RecognitionProviderException`) for real transport/auth/config failures.
3. Register it in `recognition/RecognitionProviderFactory.kt`.
4. Add any needed credential fields to `data/SettingsRepository.kt` and document them in
   `docs/CONFIGURATION.md`.
5. Add unit tests under `src/test/.../recognition/` -- mock HTTP with `okhttp3.mockwebserver.MockWebServer`
   (see `AcrCloudProviderTest.kt`) rather than hitting real third-party APIs.

## Adding a post target

1. Implement `targets/PostTarget` (see `targets/impl/WebhookTarget.kt` for the generic pattern, or
   `targets/impl/GenericOAuthTarget.kt` if the platform authenticates with a simple bearer token against a
   single "create a post" endpoint).
2. Register the `TargetType` enum value and a branch in `targets/PostTargetFactory.kt`.
3. Document the required `settings` keys and a working template example in `docs/CONFIGURATION.md`.
4. Add unit tests under `src/test/.../targets/`.

## Pull requests

- CI runs lint, unit tests + coverage, instrumented tests, and a debug APK assembly on every PR -- all must
  pass.
- New providers/targets need accompanying tests (see above).
- Keep `docs/CONFIGURATION.md` in sync with any new settings keys or template placeholders.
