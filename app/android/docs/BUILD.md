# Building AmbientNotes

## Debug builds

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use applicationId suffix `.debug` (see
`app/build.gradle.kts`) so a debug build can be installed side-by-side with a release build.

## Release builds

Release builds are minified (R8) using `proguard-rules.pro`. You'll need a signing key:

```bash
keytool -genkey -v -keystore ambient-notes-release.keystore \
  -alias ambient-notes -keyalg RSA -keysize 2048 -validity 10000
```

Keep `ambient-notes-release.keystore` **out of git** (already covered by `.gitignore`). Create
`keystore.properties` (also git-ignored) in the project root:

```properties
storeFile=/absolute/path/to/ambient-notes-release.keystore
storePassword=your-store-password
keyAlias=ambient-notes
keyPassword=your-key-password
```

Then wire it into `app/build.gradle.kts`'s `android { signingConfigs { ... } }` block (a `release` signing
config referencing the properties file above - omitted from the default build script so a fresh checkout
never fails to build for lack of a keystore; add it before you cut your first signed release).

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

## Coverage & lint reports

```bash
./gradlew testDebugUnitTest jacocoTestReport   # app/build/reports/jacoco/jacocoTestReport/html/index.html
./gradlew lint                                  # app/build/reports/lint-results-debug.html
```

## Continuous Integration

Every pull request runs [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml):
1. Lint
2. Unit tests + Jacoco coverage report (uploaded as a build artifact)
3. Instrumented tests on a headless emulator (API 34)
4. Debug APK assembly (uploaded as a build artifact you can download and sideload straight from the PR's
   Actions run, without building locally)
