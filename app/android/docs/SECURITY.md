# Security Notes - AmbientNotes (Android)

## Credential storage

Recognition-source and target credentials (API keys, access tokens, app passwords) are currently stored via
Jetpack **DataStore Preferences** (`data/SettingsRepository.kt`), which persists to app-private storage --
protected by the standard Android app sandbox on a non-rooted device, but **not encrypted at rest**.

For a stronger guarantee, wrap written values with **Jetpack Security's `EncryptedSharedPreferences`**
(already a project dependency, `androidx.security:security-crypto`) before persisting. This is flagged as a
deliberate follow-up rather than done by default, to keep the storage format simple to reason about and unit
test; see the `NOTE ON SECRETS` doc comment in `SettingsRepository.kt`.

## Network communication

- All recognition-source and target requests go over HTTPS to the respective provider's own API (ACRCloud,
  AudD, Mastodon instance, Bluesky PDS, etc.) - the app does not proxy these through any Anthropic/AmbientNotes-
  operated server.
- The self-hosted service connection should use HTTPS or a private network (VPN/Tailscale) - see
  [AmbientNotesService's SECURITY.md](../../ambient-notes-service/docs/SECURITY.md) for setting that up.

## Permissions

- `RECORD_AUDIO` - required for ambient listening; requested at runtime, revocable anytime in system
  Settings, and the listening loop no-ops gracefully if not granted (see `AudioCaptureService.captureAndRecognizeOnce`).
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` - required so listening survives Doze/background
  restrictions; Android always shows a persistent notification while this is active, by OS design, so the
  user always has a visible indicator that the mic may be sampled.
- `POST_NOTIFICATIONS` - required on Android 13+ to show that same listening notification.
- `RECEIVE_BOOT_COMPLETED` - only used to resume listening after reboot, and only if the user had explicitly
  enabled listening before the reboot (see `BootCompletedReceiver.kt`) - it's not a way to silently
  auto-start on first install.

## What this app does NOT do

- It does not continuously stream raw audio anywhere; each cycle records a short local clip, sends it only
  to the recognition source(s) you've enabled, and discards it.
- It does not access the microphone in the background beyond the user-configured sampling interval, and only
  while the foreground service (with its visible notification) is running.
- It does not bundle or share the app authors' own cloud API keys - every cloud recognition source and every
  authenticated post target requires the user's own account/credentials, so there's no shared quota or
  shared attack surface across users.

## Reporting a vulnerability

Please open a private security advisory on the GitHub repository rather than a public issue.
