#!/usr/bin/env bash
# Stops the stack and DELETES local data volumes (PostgreSQL, Redis, Kafka), then starts fresh.
# Demo seed data is recreated on startup when SECUREBANK_DEMO_SEED=true.
set -euo pipefail
cd "$(dirname "$0")/.."
read -r -p "This deletes all local SecureBank data volumes. Continue? [y/N] " answer
[[ "$answer" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
docker compose down --volumes --remove-orphans
docker compose up -d --build
echo "Stack is starting. Follow with: docker compose ps"
