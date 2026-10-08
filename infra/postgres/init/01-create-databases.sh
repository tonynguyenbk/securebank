#!/bin/bash
# One PostgreSQL container, one database per service (spec §34 allows this for local development).
# Each service owns its schema through its own Flyway migrations.
set -euo pipefail

for db in identity banking fraud audit notification; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE $db OWNER $POSTGRES_USER'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done
