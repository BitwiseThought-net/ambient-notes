# Contributing to AmbientNotesService

## Dev environment

```bash
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
cp .env.example .env
```

Run the API locally without Docker (Dejavu provider will report unavailable unless you also have a MySQL
instance and the `dejavu` package installed locally - that's fine for most feature work, since tests mock
providers rather than hitting a real DB):

```bash
uvicorn app.main:app --reload --port 8080
```

## Running checks locally (mirrors CI)

```bash
./scripts/run_tests.sh
```

Individually:

```bash
ruff check app tests          # lint
mypy app --ignore-missing-imports
pytest --cov=app --cov-report=term-missing --cov-fail-under=90
```

## Adding a recognition provider

See [docs/PROVIDERS.md](docs/PROVIDERS.md).

## Pull requests

- CI (`.github/workflows/ci.yml`) runs lint, tests + coverage, and a Docker build on every PR - all three
  must pass.
- New providers or endpoints need accompanying tests; mock external HTTP calls with `respx`
  (see `tests/test_providers.py` for the pattern) rather than hitting real third-party APIs.
- Keep `docs/API.md` in sync with any request/response schema changes in `app/models.py`.
