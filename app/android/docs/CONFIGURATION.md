# Configuring AmbientNotes On-Device

All configuration lives under **Settings** in the app. This doc covers what each field means and where to
get the credentials, since the in-app UI (see `ui/screens/SettingsScreen.kt`) intentionally stays thin and
defers the "how do I get an API key" explanation here.

## Recognition sources

Enable one or more, and set the try-order (top to bottom = first tried). AmbientNotes stops at the first
confident match — see `recognition/RecognitionOrchestrator.kt`.

### ACRCloud

1. Sign up at https://www.acrcloud.com and create a project (Audio & Video Recognition → Create).
2. From the project's console, copy the **Host**, **Access Key**, and **Access Secret**.
3. Enter them in Settings → ACRCloud.

Note: this is your own ACRCloud account/quota, not a shared key bundled with the app.

### AudD

1. Sign up at https://audd.io and copy your **API token** from the dashboard.
2. Enter it in Settings → AudD.

### ShazamKit

Apple's ShazamKit currently has **no official Android SDK**. It's listed as a selectable option so it's
visible as a first-class citizen of the recognition-source list per the project's design, but
`ShazamKitProvider` always reports itself unconfigured and will refuse to run if selected. If Apple ships an
Android SDK in the future, or you want to bridge via a Mac/iOS companion device running an HTTP shim, that's
a natural place to extend `ShazamKitProvider.kt` — the rest of the app doesn't need to change.

### Self-hosted service

1. Stand up [AmbientNotesService](../../ambient-notes-service) — see its own `docs/SETUP.md`.
2. In Settings → Self-hosted service, enter:
   - **Base URL**: e.g. `https://ambient-notes.your-domain.com` or a Tailscale/VPN address. Use HTTPS/VPN in
     production, not a bare `http://` LAN IP exposed to the internet — see that project's `docs/SECURITY.md`.
   - **API key**: the value of `API_KEYS` from the service's `.env`.

## Listening behavior

- **Listening interval**: how often (in seconds, default 60) the app records a short sample and attempts
  recognition. Shorter = catches song changes faster, uses more battery/data/API quota. Longer = the
  opposite. Adjustable in Settings → Listening.
- Recently-identified songs are de-duplicated for roughly 3 sampling intervals so a song playing continuously
  doesn't get logged/posted repeatedly (see `AudioCaptureService.logAndPost`).

## Post targets

Add any number of targets, or none. Each target has a **body template** using `{{placeholder}}` syntax (see
`targets/TemplateEngine.kt` for the full field list: `title`, `artist`, `album`, `releaseDate`, `confidence`,
`provider`, `recognizedAt`, and `externalIds.<key>` for provider-specific IDs like `externalIds.spotify`).

### Webhook (generic)

- **URL**: your endpoint.
- **Payload format**: JSON, XML, SOAP, or plain text — the template is escaped appropriately for whichever
  you pick.
- **Headers**: add `header:<Name>` settings entries for custom headers (e.g. a shared-secret auth header).
- **SOAP action**: optional `soapAction` setting, sent as the `SOAPAction` HTTP header.

Example JSON template:
```json
{"event": "song_identified", "title": "{{title}}", "artist": "{{artist}}", "confidence": {{confidence}}}
```

Example plain-text template:
```
Now playing: {{title}} by {{artist}} (via {{provider}})
```

### Mastodon

1. On your instance, go to Settings → Development → New Application. Scope: `write:statuses` is enough.
2. Copy the generated access token.
3. In-app: **Instance base URL** (e.g. `https://mastodon.social`), **Access token**.
4. Body template is plain text and becomes the post content directly, e.g. `🎵 {{title}} — {{artist}}`.

### Bluesky

1. On bsky.app: Settings → App Passwords → Add App Password. **Do not use your main account password.**
2. In-app: **Identifier** (your handle or email), **App password**, optionally **PDS base URL** (defaults to
   `https://bsky.social`).
3. Body template is plain text, e.g. `🎵 Now playing: {{title}} by {{artist}}`.

### X / Twitter, Threads, Facebook, Reddit, LinkedIn, Tumblr

These all authenticate via an **access token you obtain from that platform's own developer portal** —
AmbientNotes does not implement each platform's interactive OAuth consent flow in-app (each has its own
app-review process and redirect URI requirements that are inherently a per-developer setup step, not
something a generic client app can do for you). What's needed:

| Platform | Developer portal | Extra required setting | Notes |
|---|---|---|---|
| X / Twitter | developer.twitter.com | — | POSTs to `/2/tweets`; template should render `{"text": "..."}` |
| Threads | developers.facebook.com/docs/threads | `threadsUserId` | Two-step publish under the hood is simplified to one call here; template should render the Threads media-publish JSON body |
| Facebook | developers.facebook.com | `facebookPageId` | Posts to a Page's `/feed` |
| Reddit | reddit.com/prefs/apps | `userAgent` (recommended) | Reddit requires a descriptive User-Agent; template should render `sr`, `kind`, `title`, etc. per Reddit's submit API |
| LinkedIn | developer.linkedin.com | `linkedinAuthorUrn` | UGC Posts API; template should render the full UGC post JSON body |
| Tumblr | tumblr.com/oauth/apps | `tumblrBlogIdentifier` | e.g. `myblog.tumblr.com` |

Paste the resulting access token into the target's **Access token** field. If a token expires (several of
these are short-lived unless you implement refresh-token handling yourself), the target will start failing
with a 401 — that failure is isolated to that one target and never blocks your other targets or local
history logging (see `targets/TargetPostingCoordinator.kt`).
