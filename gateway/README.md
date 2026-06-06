# Account Gateway

OpenResty gateway является единственной публичной точкой входа для frontend и
Account API.

## Ответственность

- обслуживает SPA через внутренний frontend service;
- проверяет RS256 JWT и активность `sid`;
- при Redis miss выполняет защищённый backend session-check;
- применяет CSRF и точный credentialed CORS allowlist;
- формирует доверенный client IP из настроенных proxy CIDR;
- ограничивает частоту auth, recovery, session и avatar запросов;
- унифицирует ошибки `401`, `403`, `429` и `503`;
- проксирует публичные аватары из MinIO.

Если Redis и backend session-check недоступны, защищённый запрос получает
`503`, а не проходит без проверки.

## Маршрутизация

| Маршрут | Upstream |
|---|---|
| `/api/auth/*` | backend с CSRF/rate-limit правилами |
| `/api/users/*`, `/api/sessions`, `/api/search/*` | backend после JWT-проверки |
| `/api/avatars/*` | MinIO |
| `/swagger-ui`, `/openapi.json` | backend; Swagger можно отключить |
| `/` | frontend |
| `/health` | health gateway |

## Конфигурация

| Переменная | Назначение |
|---|---|
| `APP_ENV` | режим запуска |
| `ACCOUNT_ALLOWED_ORIGINS` | точный comma-separated CORS allowlist |
| `ACCOUNT_TRUSTED_PROXY_CIDRS` | доверенные proxy CIDR |
| `ACCOUNT_DNS_RESOLVER` | DNS resolver OpenResty |
| `ACCOUNT_HSTS_HEADER` | значение HSTS |
| `ACCOUNT_CSP` | Content-Security-Policy |
| `ACCOUNT_*_RATE` | rate limits по группам маршрутов |
| `IDENTITY_JWT_*` | публичный ключ, issuer и audience |
| `IDENTITY_INTERNAL_AUTH_SECRET` | shared secret backend session-check |
| `IDENTITY_REDIS_HOST`, `IDENTITY_REDIS_PORT` | Redis active-session cache |

В production обязательны allowed origins, HSTS, trusted proxies и internal
secret длиной от 32 символов. Wildcard origin для credentialed CORS запрещён.

## Проверки

Из директории `gateway/`:

```sh
lua tests/test_browser_security.lua
lua tests/test_jwt_auth.lua
lua tests/test_session_status.lua
```

RS256-тест требует `cjson.safe`, OpenSSL Lua bindings и тестовый JWT:

```sh
TEST_RS256_TOKEN=... lua tests/test_rs256_token.lua
```

## Production

TLS завершается на доверенном ingress/reverse proxy. Он должен быть единственной
публичной точкой входа. Не публикуйте gateway в обход ingress и не передавайте
пользовательский `X-Forwarded-For` без очистки.
