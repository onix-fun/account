# Примеры развёртывания

| Директория | Сценарий |
|---|---|
| [`docker-compose/`](docker-compose/) | Single-node reference с готовыми образами и SigNoz |
| [`kubernetes/`](kubernetes/) | Kubernetes с Ingress, PVC, NetworkPolicy и SigNoz Helm values |

## Single-node Docker Compose

```sh
cd examples/docker-compose

# 1. Сгенерировать RSA-ключи для JWT
./generate-keys.sh

# 2. Скопировать и настроить .env (опционально)
cp .env.example .env

# 3. Запустить immutable images
docker compose up -d
```

| Сервис | URL |
|---|---|
| Frontend | `http://localhost:5174` |
| Gateway / API | `http://localhost:8089` |
| Swagger UI | `http://localhost:8089/swagger-ui` |
| MailHog | `http://localhost:8026` |
| MinIO Console | `http://localhost:9011` |
| SigNoz | `http://localhost:3301` |

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
