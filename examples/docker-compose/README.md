# Docker Compose

Production-пример для одного сервера. TLS должен завершаться на доверенном
reverse proxy, который пересылает запросы во внутренний `account-gateway:8089`.

## Подготовка

```sh
cp .env.example .env
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out secrets/account-jwt-private.pem
openssl rsa -pubout -in secrets/account-jwt-private.pem \
  -out secrets/account-jwt-public.pem
```

Замените все `CHANGE_ME` в `.env`. Особенно важны:

- `PUBLIC_BASE_URL` и точный `ACCOUNT_ALLOWED_ORIGINS`;
- `ACCOUNT_TRUSTED_PROXY_CIDRS`;
- `IDENTITY_OTP_HMAC_SECRET` и `IDENTITY_INTERNAL_AUTH_SECRET`;
- database, SMTP STARTTLS и S3 credentials.

## Запуск

```sh
docker compose config
docker compose up -d
docker compose ps
```

Gateway имеет только внутренний `expose`. Настройте reverse proxy в той же сети
или явно добавьте безопасную публикацию порта только для доверенного ingress.

## Обновление и остановка

```sh
docker compose pull
docker compose up -d
docker compose down
```

`docker compose down -v` необратимо удаляет PostgreSQL, Redis и MinIO volumes.

## Ограничения

Пример удобен для небольшого single-host deployment, но не обеспечивает HA.
Не публикуйте backend, PostgreSQL, Redis или MinIO напрямую.
