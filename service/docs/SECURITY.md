# Security Guide — AmbientNotesService

This service receives short audio clips captured by your phone's microphone and (optionally) forwards them
to third-party cloud APIs. Treat it like any other internet-facing service handling personal data.

## 1. Authentication

- Every `/api/v1/recognize` call requires `Authorization: Bearer <key>`, checked against `API_KEYS` in
  `.env`. The server **refuses to start accepting recognition requests** (`503`) if `API_KEYS` is unset —
  there is no "open" mode.
- Use a long, random key (`openssl rand -hex 32`, 64 hex chars / 256 bits). Don't reuse it elsewhere.
- **Rotation**: set `API_KEYS=old-key,new-key` (comma-separated), update the Android app to the new key,
  confirm it's working, then remove `old-key` and restart the service. Both keys are valid simultaneously
  during the overlap window.
- `/api/v1/health` and `/api/v1/providers` are intentionally unauthenticated (no sensitive data) so you can
  wire them into uptime monitors without embedding a key.

## 2. Transport encryption (TLS)

The FastAPI app itself speaks plain HTTP. **Do not expose port 8080 directly to the internet.** Put a
TLS-terminating reverse proxy in front of it:

### Option A: Caddy (simplest, automatic Let's Encrypt certs)

```
# Caddyfile
ambient-notes.your-domain.com {
    reverse_proxy localhost:8080
}
```

```bash
docker run -d --name caddy -p 443:443 -p 80:80 \
  -v $PWD/Caddyfile:/etc/caddy/Caddyfile \
  -v caddy-data:/data \
  caddy:2
```

### Option B: nginx + certbot, or your existing reverse proxy / tunnel (Cloudflare Tunnel, Tailscale
Funnel, Traefik) — any of these work; the requirement is just "TLS terminates before traffic reaches
port 8080."

### Option C (recommended for personal use): don't expose it publicly at all

Put the service on a **Tailscale** or **WireGuard** network alongside your phone. The Android app talks to
its private IP/hostname; nothing is reachable from the open internet. This sidesteps the cert-management
question entirely and is the setup this project's own testing assumes.

`REQUIRE_TLS=true` in `.env` is a soft guard for the app layer; it does not itself terminate TLS — the
proxy/tunnel does that work.

## 3. Network exposure

- Only publish the port you actually need. `docker-compose.yml` binds `dejavu-db` and `ollama` to the
  internal `ambient-notes-net` bridge network only — they are **not** published to the host by default.
  Don't add `ports:` entries for them unless you specifically need external access (e.g. debugging with a
  MySQL client), and if you do, restrict via firewall rules to your LAN/VPN.
- Consider a firewall rule limiting inbound connections to the service port to your phone's expected IP
  range (harder with mobile carriers/roaming — a VPN/tailnet is more practical for most people).

## 4. Secrets hygiene

- `.env` is git-ignored by default (see `.gitignore`) — never commit real credentials.
- `ACRCLOUD_ACCESS_SECRET` and `AUDD_API_TOKEN`, if set, are the *operator's* cloud credentials, shared
  across all users of this instance. Only enable these fallbacks if you're comfortable with that; per-user
  ACRCloud/AudD/ShazamKit accounts are handled directly by the Android app and never touch this service.
- If you use the `selfhosted_peer` fallback to federate with a friend's instance, that peer's URL and API
  key are also secrets — treat `PEER_SERVICE_URLS` accordingly and use a dedicated key for the peer
  relationship, not your primary phone's key.

## 5. Data handling

- Audio clips are processed in memory for the duration of a single request and are **not persisted to disk
  or a database** by this service. Recognition results (song metadata) also aren't stored server-side —
  logging of identified songs happens on the Android app, per your configured targets.
- Application logs (`LOG_LEVEL`) do not include raw audio or API keys. Avoid setting `LOG_LEVEL=DEBUG` in
  production if you're piping logs to a third-party aggregator you don't fully trust, since request
  metadata (device_id, provider responses) will be more verbose.

## 6. Keeping the image up to date

`docker compose pull && docker compose up -d --build` periodically to pick up base-image security patches
(`python:3.12-slim`, `mariadb:11`, `ollama/ollama`). The CI workflow rebuilds and tests the image on every
change to this repo, but it's on you to redeploy.

## 7. Reporting a vulnerability

Please open a private security advisory on the GitHub repository rather than a public issue.
