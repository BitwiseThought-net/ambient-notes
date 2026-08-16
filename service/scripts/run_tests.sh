#!/usr/bin/env bash
# Runs the full lint + test + coverage suite locally, mirroring CI.
set -euo pipefail
cd "$(dirname "$0")/.."
python -m pip install -q -r requirements-dev.txt
ruff check app tests
mypy app --ignore-missing-imports || true  # non-fatal locally; CI enforces separately
pytest --cov=app --cov-report=term-missing --cov-fail-under=90 "$@"
