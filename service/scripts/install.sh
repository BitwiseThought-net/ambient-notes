#!/usr/bin/env bash
# Convenience wrapper: runs setup.sh then brings the stack up.
# Usage: ./scripts/install.sh [--with-ollama]
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/setup.sh

if [[ "${1:-}" == "--with-ollama" ]]; then
  echo "Starting stack with local Ollama profile enabled..."
  docker compose --profile ollama up -d --build
else
  docker compose up -d --build
fi

echo "Waiting for service to become healthy..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:"${AMBIENT_NOTES_PORT:-8080}"/api/v1/health >/dev/null 2>&1; then
    echo "AmbientNotesService is up."
    exit 0
  fi
  sleep 2
done

echo "Service did not become healthy in time. Check logs with: docker compose logs -f" >&2
exit 1
