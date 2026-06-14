# Docker Compose quickstart

```sh
cp .env.example .env
./generate-keys.sh
docker compose up -d
```

Compose запускает внешний nginx, frontend, backend, one-shot миграции,
PostgreSQL, Redis, MinIO и MailHog.
