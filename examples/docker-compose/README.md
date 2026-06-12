# Docker Compose — production reference

Запускает immutable application images и полный self-hosted SigNoz stack.
Локальная сборка образов в этом примере отсутствует.

## Быстрый старт

```sh
./generate-keys.sh
cp .env.example .env
docker compose up -d
```

| Сервис | URL |
|---|---|
| Frontend | `http://localhost:5174` |
| Gateway / API | `http://localhost:8089` |
| Swagger UI | `http://localhost:8089/swagger-ui` |
| MailHog (email) | `http://localhost:8026` |
| MinIO Console | `http://localhost:9011` |
| SigNoz | `http://localhost:3301` |

## Переменные окружения

Скопируйте `.env.example` в `.env` и при необходимости измените порты:

```sh
cp .env.example .env
```

После создания первого администратора SigNoz импортируйте dashboards:

```sh
SIGNOZ_API_KEY=replace-me ../../observability/signoz/import-dashboards.sh
```

## Остановка

```sh
docker compose down        # остановить контейнеры
docker compose down -v     # удалить application и SigNoz volumes
```

## Production

Для production используйте образы из GHCR:

```text
ghcr.io/onix-fun/accaunt/frontend
ghcr.io/onix-fun/accaunt/backend
ghcr.io/onix-fun/accaunt/gateway
```

Требования: HTTPS, Secure cookies, CORS allowlist, SMTP STARTTLS,
секреты длиной не менее 32 символов.
