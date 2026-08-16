#!/usr/bin/env bash
# One-time setup: creates .env from the example template if it doesn't
# already exist, and generates a random API key so you're not shipping the
# placeholder value. Safe to re-run (won't overwrite an existing .env).
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  echo ".env already exists, leaving it untouched."
else
  cp .env.example .env
  if command -v openssl >/dev/null 2>&1; then
    KEY=$(openssl rand -hex 32)
    # portable in-place sed for macOS/Linux
    sed -i.bak "s/^API_KEYS=.*/API_KEYS=${KEY}/" .env && rm -f .env.bak
    echo "Generated a random API key and wrote it to .env"
    echo "Give this exact value to the Android app's 'Self-hosted Service' settings screen:"
    echo "  ${KEY}"
  else
    echo "openssl not found -- edit .env manually and set API_KEYS to a long random string."
  fi
fi

echo
echo "Next steps:"
echo "  1. Review .env and adjust PROVIDER_CHAIN / provider credentials as needed."
echo "  2. Run: docker compose up -d --build"
echo "  3. Check health: curl http://localhost:8080/api/v1/health"
