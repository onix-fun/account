# Account Backend

Kotlin/Ktor API для авторизации, профилей, сессий, поиска и аватаров.

## Основные возможности

- пошаговая регистрация, вход и восстановление пароля;
- подтверждение email и безопасная смена email через текущий пароль;
- access JWT RS256 и opaque refresh tokens;
- немедленный отзыв сессий через PostgreSQL и Redis;
- одноразовые HMAC-коды с лимитом попыток и повторной отправки;
- PostgreSQL outbox для надёжной доставки email;
- обработка и очистка метаданных аватаров перед сохранением в S3;
- структурированные ошибки и security-аудит;
- опциональный gRPC-сервер с mTLS.

## Зависимости

| Компонент | Назначение |
|---|---|
| PostgreSQL 16 | пользователи, сессии, challenges, outbox и audit |
| Redis 7 | активные сессии, rate limit и незавершённая регистрация |
| S3/MinIO | хранение аватаров |
| SMTP STARTTLS | отправка писем |

Flyway создаёт финальную схему из
`src/main/resources/db/migration/V1__init_schema.sql`.

## Запуск и тесты

Для полного локального окружения:

```sh
make up
```

Из директории `backend/`:

```sh
mvn clean test
mvn clean package -DskipTests
```

Backend health endpoint: `GET /health`.

## API

Основные группы:

| Маршрут | Назначение |
|---|---|
| `/api/auth/*` | регистрация, lookup, login, refresh, reset и подтверждение |
| `/api/users/*` | профиль, аватар и безопасная смена email |
| `/api/sessions` | список и отзыв сессий |
| `/api/search/search` | поиск пользователей |
| `/internal/session-check` | закрытая проверка сессии для gateway |
| `/openapi.json` | OpenAPI-спецификация |

Единый формат ошибки:

```json
{
  "code": "AUTH_INVALID_PASSWORD",
  "numericCode": 2002,
  "message": "Invalid password",
  "fieldErrors": [
    { "field": "password", "code": "AUTH_INVALID_PASSWORD", "numericCode": 2002 }
  ],
  "requestId": "..."
}
```

Поле `message` предназначено для диагностики. Пользовательский текст выбирает
frontend по `code`.

## Конфигурация

Все параметры читаются из переменных окружения через `application.yaml`.

| Переменная | Назначение |
|---|---|
| `APP_ENV` | `development` или `production` |
| `IDENTITY_DATABASE_*` | PostgreSQL JDBC URL и credentials |
| `IDENTITY_REDIS_URL` | Redis URL |
| `IDENTITY_JWT_*` | issuer, audience и пути к RSA-ключам |
| `IDENTITY_OTP_HMAC_SECRET` | HMAC-секрет одноразовых кодов |
| `IDENTITY_INTERNAL_AUTH_SECRET` | shared secret внутреннего session-check |
| `IDENTITY_SMTP_*` | SMTP, auth, from и STARTTLS |
| `IDENTITY_S3_*` | endpoint, публичный HTTPS URL и S3 credentials |
| `IDENTITY_GRPC_*` | enable-флаг, mTLS-файлы, SAN allowlist и reflection |

Production fail-fast требует:

- `IDENTITY_REFRESH_COOKIE_SECURE=true`;
- HMAC и internal-auth секреты длиной от 32 символов;
- `IDENTITY_SMTP_STARTTLS=true` и валидный `IDENTITY_SMTP_FROM`;
- HTTPS в `IDENTITY_S3_PUBLIC_URL`.

## Коды подтверждения

- срок действия: 15 минут;
- максимум 5 неправильных попыток;
- старый challenge инвалидируется при создании нового;
- после блокировки требуется новый код;
- reset-password инвалидирует reset-коды и все сессии пользователя.

Не логируйте пароли, коды, токены и открытые recovery-идентификаторы.
