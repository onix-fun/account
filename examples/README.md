# Примеры развёртывания

| Директория | Сценарий |
|---|---|
| [`docker-compose/`](docker-compose/) | Локальный тестовый запуск (build из исходников, все сервисы) |
| [`kubernetes/`](kubernetes/) | Kubernetes с Ingress, PVC, Services и NetworkPolicy |

## Локальный тестовый запуск (Docker Compose)

```sh
cd examples/docker-compose

# 1. Сгенерировать RSA-ключи для JWT
./generate-keys.sh

# 2. Скопировать и настроить .env (опционально)
cp .env.example .env

# 3. Запустить
docker compose up --build
```

| Сервис | URL |
|---|---|
| Frontend | `http://localhost:5174` |
| Gateway / API | `http://localhost:8089` |
| Swagger UI | `http://localhost:8089/swagger-ui` |
| MailHog | `http://localhost:8026` |
| MinIO Console | `http://localhost:9011` |

Frontend собирается в production-режиме (multi-stage Dockerfile).

## Production

Production-образы публикуются в GHCR:

```text
ghcr.io/onix-fun/accaunt/frontend
ghcr.io/onix-fun/accaunt/backend
ghcr.io/onix-fun/accaunt/gateway
```

Перед запуском замените домены, секреты, SMTP, trusted proxy CIDR и image tags.
Не коммитьте `.env`, TLS-ключи и JWT-ключи.

Production должен использовать HTTPS. Backend, PostgreSQL, Redis и MinIO должны
оставаться во внутренней сети.
