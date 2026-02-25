#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[desktop-smoke] compile desktop module"
./gradlew -q :desktop-app:compileKotlin

echo "[desktop-smoke] run fast regression suite"
bash scripts/run-tests.sh

echo "[desktop-smoke] automated checks passed"
echo "Manual UI checklist: docs/desktop-smoke-checklist.md"
