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

PostgreSQL, Redis и SMTP обязательны; Profile и Media доступны по внутреннему
gRPC. Account не хранит public profile и avatar blobs: он проксирует команды
в сервисы-владельцы. Все настройки
задаются через `ACCOUNT_*`; секреты дополнительно поддерживают `*_FILE`.

Основные группы: `ACCOUNT_DATABASE_*`, `ACCOUNT_REDIS_*`, `ACCOUNT_SMTP_*`,
`ACCOUNT_PROFILE_*`, `ACCOUNT_MEDIA_*`, `ACCOUNT_JWT_*`, `ACCOUNT_HTTP_*`, `ACCOUNT_GRPC_*`,
`ACCOUNT_SECURITY_*`.

В production обязательны Secure cookies, SMTP STARTTLS и
mTLS для включенного gRPC.

HTTP API сохраняет `/api/*`, `/openapi.json` и `/swagger-ui`. Служебные
endpoints: `/livez`, `/readyz`, `/metrics`, `/.well-known/jwks.json`.
