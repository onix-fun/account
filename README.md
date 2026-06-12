# Account

Сервис аккаунтов, авторизации и профилей. Монорепозиторий содержит frontend,
backend, публичный gateway и готовые примеры развёртывания.

## Компоненты

| Директория | Назначение |
|---|---|
| `frontend/` | Vue 3 SPA: вход, регистрация, восстановление, профиль и сессии |
| `backend/` | Kotlin/Ktor API, PostgreSQL, Redis, email outbox и S3-аватары |
| `gateway/` | OpenResty: единая публичная точка входа, JWT, CSRF, CORS и rate limit |
| `dev/` | Локальная Docker Compose-инфраструктура и генерация RSA-ключей |
| `examples/` | Примеры развёртывания (локальный тест, Kubernetes) |
| `observability/` | Edge OpenTelemetry Collector и self-hosted SigNoz stack |

Публичный трафик проходит через gateway. Backend, PostgreSQL, Redis и MinIO не
должны быть доступны из интернета напрямую.

## Локальный запуск

Требования: Docker, Node.js 22+, npm, OpenSSL и Make.

```sh
make up
```

Команда:

1. создаёт development RSA-ключи в `dev/secrets/`;
2. запускает backend, gateway, PostgreSQL, Redis, MinIO, MailHog и OTel;
3. запускает Vite frontend с hot reload.

Адреса:

| Сервис | URL |
|---|---|
| Frontend | `http://localhost:5174` |
| Gateway/API | `http://localhost:8089` |
| Swagger UI | `http://localhost:8089/swagger-ui` |
| MailHog | `http://localhost:8026` |
| MinIO Console | `http://localhost:9011` |
| SigNoz | `http://localhost:3301` |
| OTel Collector health | `http://localhost:13133` |

Управление окружением:

```sh
make down   # остановить контейнеры
make clean  # остановить и удалить локальные volumes
make reset-db # пересоздать только PostgreSQL из init-миграций
```

Пока проект не выпущен в production, итоговая схема хранится в
`V1__init_schema.sql`. После намеренного изменения этой миграции используйте
`make reset-db`: команда удаляет только dev-volume PostgreSQL и не затрагивает
данные Redis, MinIO и SigNoz.

При первом запуске создайте администратора в SigNoz. Затем создайте API key в
Settings и импортируйте подготовленные инфраструктурные dashboards:

```sh
SIGNOZ_API_KEY=replace-me ./observability/signoz/import-dashboards.sh
```

## Проверки

```sh
cd backend && mvn clean test
cd frontend && npm test -- --run && npm run build
cd gateway && lua tests/test_browser_security.lua
cd gateway && lua tests/test_jwt_auth.lua
cd gateway && lua tests/test_session_status.lua
```

## Безопасность

- Пароли хешируются Argon2id.
- Одноразовые коды хешируются контекстным HMAC-SHA-256, имеют срок действия,
  лимит попыток и cooldown повторной отправки.
- Access JWT привязан к активной сессии. Gateway и backend отклоняют отозванные
  сессии.
- Browser-запросы используют Secure/HttpOnly cookies и CSRF-защиту.
- Email отправляется через зашифрованный PostgreSQL outbox с retry.
- Ошибки API имеют стабильные строковые и числовые коды. Frontend показывает
  только локализованные сообщения.

## Production

Используйте один из примеров:

- [`examples/docker-compose/`](examples/docker-compose/) — локальный тестовый
  запуск или сервер за доверенным HTTPS reverse proxy;
- [`examples/kubernetes/`](examples/kubernetes/) — Kubernetes, TLS Ingress,
  Services, PVC и NetworkPolicy.

При `APP_ENV=production` backend и gateway завершают запуск при небезопасной
конфигурации. Обязательны HTTPS, Secure cookies, точный CORS allowlist,
доверенные proxy CIDR, SMTP STARTTLS и секреты длиной не менее 32 символов.

## Релизы

GitHub Actions публикует образы в GHCR по тегам:

```text
front_v1.0.0
back_v1.0.0
gate_v1.0.0
```

Перед релизом запускайте тесты всех изменённых компонентов.
