# AmbientNotesService

Self-hosted, always-on audio recognition backend for the [AmbientNotes](../ambient-notes) Android app.

It's completely optional -- AmbientNotes can identify songs using ACRCloud, AudD, or ShazamKit directly with
your own cloud account. Run AmbientNotesService if you'd rather keep recognition local (via a
[Dejavu](https://github.com/worldveil/dejavu)-style fingerprint database you own) and only fall back to
cloud/LLM providers when the local library misses.

## Features

- **FastAPI** HTTP API, single endpoint the Android app calls: `POST /api/v1/recognize`.
- **Pluggable provider chain** — recognize locally first (Dejavu), then fall back to a local LLM
  (Ollama), then to cloud services (ACRCloud, AudD), then to peer AmbientNotesService instances —
  in whatever order you configure, no recompilation needed.
- **Drop-in providers** — add a new backend by writing one Python module and registering it; see
  [docs/PROVIDERS.md](docs/PROVIDERS.md).
- **Docker Compose** deployment: `docker compose up -d --build` and you're running.
- **API-key authentication** and guidance for putting the service behind TLS.

## Quick start

```bash
git clone https://github.com/<your-org>/ambient-notes-service.git
cd ambient-notes-service
./scripts/install.sh
```

That generates a `.env` with a random API key, builds the containers, and starts the stack. Copy the
printed API key into the Android app's **Settings → Recognition Source → Self-hosted service** screen,
along with `http://<this-machine's-address>:8080`.

Prefer to do it by hand? See [docs/SETUP.md](docs/SETUP.md) for the manual, step-by-step version, including
how to fingerprint your own music library into Dejavu.

## Documentation

| Doc | Covers |
|---|---|
| [docs/SETUP.md](docs/SETUP.md) | Docker host prerequisites, manual setup, configuration reference, seeding Dejavu |
| [docs/SECURITY.md](docs/SECURITY.md) | API keys, TLS/reverse proxy setup, network hardening, key rotation |
| [docs/PROVIDERS.md](docs/PROVIDERS.md) | The provider plugin interface; how to add/remove/reorder recognition backends |
| [docs/API.md](docs/API.md) | Request/response schema for the HTTP API |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Dev environment, running tests, PR process |

## Development

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
./scripts/run_tests.sh          # lint + tests + coverage (mirrors CI)
uvicorn app.main:app --reload   # run the API locally without Docker
```

Tests run automatically on every pull request via [.github/workflows/ci.yml](.github/workflows/ci.yml)
and enforce a minimum of 90% line coverage on `app/` (`dejavu_provider.py`'s I/O path is exercised via
its availability guard rather than a live fingerprint DB in CI — see the coverage config in
`pyproject.toml` for the rationale).

## License

MIT — see [LICENSE](LICENSE).
