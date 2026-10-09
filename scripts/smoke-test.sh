#!/usr/bin/env bash
# Runs the spec §51 demo scenario against the running stack (default: http://localhost:8080).
#   ./scripts/smoke-test.sh [--base http://localhost:8080]
set -euo pipefail
cd "$(dirname "$0")"
PY=$(command -v python3 || command -v python)
exec "$PY" smoke_test.py "$@"
