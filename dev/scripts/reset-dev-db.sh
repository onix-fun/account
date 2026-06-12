#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEV_DIR=$(dirname "$SCRIPT_DIR")

cd "$DEV_DIR"

volume_name=$(docker volume ls -q \
  --filter label=com.docker.compose.project=dev \
  --filter label=com.docker.compose.volume=account_postgres_data)

if [ -z "$volume_name" ]; then
  echo "Development PostgreSQL volume does not exist; starting a clean database."
else
  echo "Removing development PostgreSQL data volume: $volume_name"
  docker compose stop profile account-postgres
  docker compose rm -f profile account-postgres
  docker volume rm "$volume_name"
fi

docker compose up -d --wait account-postgres
docker compose up -d --build profile account-gateway

echo "Development PostgreSQL database was recreated from init migrations."
