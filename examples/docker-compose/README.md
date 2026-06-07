# Docker Compose — локальный тестовый запуск

Собирает все сервисы из исходников и запускает полный стек.

## Быстрый старт

```sh
./generate-keys.sh
docker compose up --build
```

| Сервис | URL |
|---|---|
| Frontend | `http://localhost:5174` |
| Gateway / API | `http://localhost:8089` |
| Swagger UI | `http://localhost:8089/swagger-ui` |
| MailHog (email) | `http://localhost:8026` |
| MinIO Console | `http://localhost:9011` |

## Переменные окружения

Скопируйте `.env.example` в `.env` и при необходимости измените порты:

```sh
cp .env.example .env
```

## Остановка

```sh
docker compose down        # остановить контейнеры
docker compose down -v     # остановить и удалить volumes (БД, Redis, MinIO)
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
