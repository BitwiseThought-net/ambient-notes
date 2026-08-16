# Setup Guide — AmbientNotesService

## 1. Prerequisites

- A machine that's reachable from your phone 24/7 — a home server, NAS (with Docker support), or a small
  VPS. 1 vCPU / 1 GB RAM is enough if you're only running `dejavu` + cloud fallbacks; add more if you
  enable the local `ollama` profile (a 7-8B model wants ~8 GB RAM, more with a GPU).
- **Docker Engine 24+** and the **Compose plugin** (`docker compose version` should print `v2.x`).
  - Linux: follow https://docs.docker.com/engine/install/ for your distro, then
    https://docs.docker.com/engine/install/linux-postinstall/ so you don't need `sudo` for every command.
  - Synology/QNAP: install Docker/Container Manager from the package center; Compose v2 ships with recent
    versions.
- Optionally, `openssl` on the host (used by `scripts/setup.sh` to generate your API key — most Linux/macOS
  systems have this already).
- A domain name or dynamic-DNS hostname if you want to reach the service from outside your home network. See
  [SECURITY.md](SECURITY.md) for exposing it safely.

## 2. Get the code

```bash
git clone https://github.com/<your-org>/ambient-notes-service.git
cd ambient-notes-service
```

## 3. Configure

```bash
cp .env.example .env
```

Open `.env` and set, at minimum:

- `API_KEYS` — a long random string (or several, comma-separated, for key rotation). This is what the
  Android app authenticates with. `openssl rand -hex 32` is a good way to generate one.
- `PROVIDER_CHAIN` — the order to try recognition backends in. The default `dejavu,ollama` tries your local
  fingerprint library first, then a local LLM guess as a last resort, and never leaves your network. Add
  `acrcloud` and/or `audd` (and set the matching `*_ENABLED`/credentials) if you want cloud fallback for
  songs your local library doesn't know.

Every setting is documented inline in `.env.example`.

`scripts/setup.sh` automates steps above (copies the template and generates a random `API_KEYS` value for
you) if you'd rather not do it by hand.

## 4. Start the stack

```bash
docker compose up -d --build
```

This starts:
- `ambient-notes-service` — the FastAPI app, on port `8080` by default (`AMBIENT_NOTES_PORT` in `.env`).
- `dejavu-db` — a MariaDB instance backing the local fingerprint database.

To also run a local Ollama instance (rather than pointing `OLLAMA_BASE_URL` at one elsewhere):

```bash
docker compose --profile ollama up -d --build
```

Verify it's healthy:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok","providers_configured":["dejavu","ollama"]}
```

## 5. Seed your local fingerprint library (optional, for the `dejavu` provider)

The `dejavu` provider can only recognize songs it has fingerprinted. Point it at a folder of your own
music files:

```bash
docker compose exec ambient-notes-service python -m app.tools.fingerprint_library /path/inside/container/to/music
```

> This project ships the provider interface and orchestration fully wired up; the batch-fingerprinting CLI
> is a thin wrapper around Dejavu's own `fingerprint_directory` API — see
> [docs/PROVIDERS.md](PROVIDERS.md#dejavu) for the few lines needed to adapt it to your own music library
> layout, since everyone's collection is organized differently.

If you skip this step, `dejavu` will simply report "no match" for everything and requests will fall through
to your next configured provider — nothing breaks.

## 6. Point the Android app at your service

In AmbientNotes: **Settings → Recognition Source → Add self-hosted service**, enter:
- **Base URL**: `https://your-domain-or-ip:8080` (use HTTPS in production — see SECURITY.md)
- **API key**: the value of `API_KEYS` from your `.env`

## 7. Updating

```bash
git pull
docker compose up -d --build
```

## 8. Logs & troubleshooting

```bash
docker compose logs -f ambient-notes-service
docker compose ps          # check container health status
docker compose down        # stop everything (add -v to also wipe the fingerprint DB volume)
```

Common issues:
- **401 from the app**: `API_KEYS` in `.env` doesn't match what's entered in the app, or you edited `.env`
  without restarting (`docker compose up -d` after any `.env` change).
- **503 "no API_KEYS configured"**: you left `API_KEYS` blank — the service refuses to run unauthenticated.
- **`dejavu` provider always "unavailable"**: the optional native build step in the Docker image failed for
  your platform; check `docker compose logs ambient-notes-service` at startup, or run with
  `PROVIDER_CHAIN=ollama` (or a cloud provider) in the meantime.
