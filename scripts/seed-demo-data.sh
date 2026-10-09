#!/usr/bin/env bash
# Demo data is seeded by the services themselves on startup (idempotent insert-if-absent)
# when SECUREBANK_DEMO_SEED=true — identity-service creates the users, banking-core-service
# the customers and accounts, both with the fixed IDs from docs/contracts/api.md §7.
# This script enables the flag in .env and restarts the two seeding services.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || cp .env.example .env
if grep -q '^SECUREBANK_DEMO_SEED=' .env; then
  sed -i.bak 's/^SECUREBANK_DEMO_SEED=.*/SECUREBANK_DEMO_SEED=true/' .env && rm -f .env.bak
else
  echo 'SECUREBANK_DEMO_SEED=true' >> .env
fi
docker compose up -d --force-recreate identity-service banking-core-service
echo "Demo users: customer1/Customer@123, customer2/Customer@123, staff1/Staff@123, auditor1/Auditor@123, admin1/Admin@123"
