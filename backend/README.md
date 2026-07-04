# Account Backend

Kotlin/Ktor backend для аккаунтов, авторизации, профилей и сессий.

## Команды

```sh
java -jar app.jar config validate
java -jar app.jar migrate
java -jar app.jar serve --role=api
java -jar app.jar serve --role=worker
java -jar app.jar serve --role=all
```

`migrate` является единственным процессом, запускающим Flyway. Runtime-роли не
изменяют схему БД.

## Зависимости

PostgreSQL, Redis, SMTP и S3-compatible storage обязательны. Redis используется
только как runtime-индекс поиска пользователей по username; auth/session/rate
state хранится в PostgreSQL. Все настройки
задаются через `ACCOUNT_*`; секреты дополнительно поддерживают `*_FILE`.

Основные группы: `ACCOUNT_DATABASE_*`, `ACCOUNT_REDIS_*`, `ACCOUNT_SMTP_*`,
`ACCOUNT_S3_*`, `ACCOUNT_JWT_*`, `ACCOUNT_HTTP_*`, `ACCOUNT_GRPC_*`,
`ACCOUNT_SECURITY_*`.

В production обязательны Secure cookies, SMTP STARTTLS, HTTPS public S3 URL и
mTLS для включенного gRPC.

HTTP API сохраняет `/api/*`, `/openapi.json` и `/swagger-ui`. Служебные
endpoints: `/livez`, `/readyz`, `/metrics`, `/.well-known/jwks.json`.
