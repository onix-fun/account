# Конфигурация

Сервис `account` настраивается полностью через переменные окружения.

## Настройки приложения

| Переменная | По умолчанию | Описание |
| :--- | :--- | :--- |
| `ACCOUNT_ENV` | `development` | `development`, `test` или `production`. |
| `ACCOUNT_HTTP_PORT` | `8080` | Публичный порт Nginx. |
| `ACCOUNT_ROLE` | `all` | Роль процесса: `api`, `worker` или `all`. |

## База данных и Redis

| Переменная | Описание |
| :--- | :--- |
| `ACCOUNT_DATABASE_JDBC_URL` | Строка подключения PostgreSQL (JDBC). |
| `ACCOUNT_DATABASE_USERNAME` | Имя пользователя БД. |
| `ACCOUNT_DATABASE_PASSWORD` | Пароль пользователя БД. |
| `ACCOUNT_REDIS_URL` | URL для подключения к Redis (например, `redis://localhost:6379`). |

## Хранилище (S3)

| Переменная | Описание |
| :--- | :--- |
| `ACCOUNT_S3_ENDPOINT` | URL эндпоинта Minio/AWS S3. |
| `ACCOUNT_S3_ACCESS_KEY` | Ключ доступа S3 Access Key. |
| `ACCOUNT_S3_SECRET_KEY` | Секретный ключ S3 Secret Key. |
| `ACCOUNT_S3_BUCKET` | Бакет для аватаров (по умолчанию: `avatars`). |
| `ACCOUNT_S3_PUBLIC_URL` | Базовый URL для раздачи загруженных файлов. |

## Безопасность и JWT

| Переменная | Описание |
| :--- | :--- |
| `ACCOUNT_JWT_PRIVATE_KEY` | Приватный RSA ключ для подписи токенов. |
| `ACCOUNT_JWT_PUBLIC_KEY` | Публичный RSA ключ для валидации. |
| `ACCOUNT_JWT_ISSUER` | Значение `iss` (issuer) в токене. |
| `ACCOUNT_SECURITY_OTP_HMAC_SECRET` | Секрет для генерации кодов подтверждения (Email OTP). |

## Конфигурация фронтенда

| Переменная | По умолчанию | Описание |
| :--- | :--- | :--- |
| `ACCOUNT_FRONTEND_BASE_PATH` | `/` | URL путь, по которому доступен фронтенд (например, `/account/`). |
| `ACCOUNT_API_BASE_URL` | `/api` | Базовый путь для запросов API из браузера. |
| `ACCOUNT_TRUSTED_REDIRECT_ORIGINS` | | Список (через запятую) разрешенных URL для редиректа после входа. |

## gRPC (mTLS)

| Переменная | Описание |
| :--- | :--- |
| `ACCOUNT_GRPC_ENABLED` | Установите `true` для запуска gRPC сервера. |
| `ACCOUNT_GRPC_PORT` | Порт для gRPC трафика (по умолчанию: `9097`). |
| `ACCOUNT_GRPC_CERTIFICATE` | SSL сертификат сервера. |
| `ACCOUNT_GRPC_PRIVATE_KEY` | Приватный SSL ключ сервера. |
| `ACCOUNT_GRPC_CLIENT_CA` | Корневой сертификат (CA) для проверки клиентов. |
