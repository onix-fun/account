# Account

Самостоятельный AGPL-3.0 сервис аккаунтов, авторизации и профилей.

Репозиторий публикует два независимо версионируемых образа:

- `ghcr.io/onix-fun/accaunt/frontend`
- `ghcr.io/onix-fun/accaunt/backend`

Frontend является нейтральной Vue SPA. Backend предоставляет HTTP API `/api/*`,
OpenAPI и служебный gRPC `ProfileService`. Публичный трафик должен проходить
через внешний reverse proxy или Kubernetes Ingress.

## Запуск

```sh
cp examples/docker-compose/.env.example examples/docker-compose/.env
cd examples/docker-compose
./generate-keys.sh
docker compose up -d
```

Пример доступен на `http://localhost:8089`. Swagger UI находится по адресу
`http://localhost:8089/swagger-ui`.

Backend конфигурируется только переменными `ACCOUNT_*`. Секреты принимаются
как прямое значение или через соответствующую переменную `*_FILE`.

```sh
docker run --rm --env-file .env ghcr.io/onix-fun/accaunt/backend:1.0.0 config validate
docker run --rm --env-file .env ghcr.io/onix-fun/accaunt/backend:1.0.0 migrate
docker run --rm --env-file .env ghcr.io/onix-fun/accaunt/backend:1.0.0 serve --role=all
```

Роли backend: `api`, `worker`, `all`. Operational endpoints:
`/livez`, `/readyz`, `/metrics`, `/.well-known/jwks.json`.

## Проверки

```sh
cd backend && mvn clean verify
cd frontend && npm ci && npm run test:coverage && npm run build
docker compose -f examples/docker-compose/docker-compose.yml config --quiet
```

## Релизы

- `front_v1.0.0` публикует frontend `1.0.0`, `1.0`, `sha-*`, `latest`.
- `back_v1.0.0` публикует backend и прикладывает canonical `profile.proto`.
- RC-теги не обновляют `latest`.

Образы публикуются для `linux/amd64` и `linux/arm64`, содержат provenance и
SBOM, проходят Trivy scan и подписываются Cosign.
