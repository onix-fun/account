# Примеры развёртывания

| Директория | Сценарий |
|---|---|
| [`docker-compose/`](docker-compose/) | один production-сервер за HTTPS reverse proxy |
| [`kubernetes/`](kubernetes/) | Kubernetes с Ingress, PVC, Services и NetworkPolicy |

Примеры используют образы:

```text
ghcr.io/onix-fun/accaunt/frontend
ghcr.io/onix-fun/accaunt/backend
ghcr.io/onix-fun/accaunt/gateway
```

Перед запуском замените домены, секреты, SMTP, trusted proxy CIDR и image tags.
Не коммитьте `.env`, `secret.yaml`, TLS-ключи и JWT-ключи.

Production должен использовать HTTPS. Backend, PostgreSQL, Redis и MinIO должны
оставаться во внутренней сети.
