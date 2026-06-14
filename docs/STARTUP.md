# Запуск и развертывание

## Требования

- **Docker** (версия 24.0+)
- **PostgreSQL** 16+
- **Redis** 7+
- **S3 Хранилище** (Minio или AWS S3)

## Процесс сборки

Сервис `account` использует мультистейдж Docker-сборку для создания единого образа.

```bash
# Сборка образа локально
docker build -t accaunt:latest .
```

## Запуск через Docker Compose

Пример `docker-compose.yml` находится в директории `examples/docker-compose`.

### Быстрый старт (режим разработки)

1. **Генерация секретов**:
   ```bash
   ./generate-keys.sh
   ```

2. **Запуск инфраструктуры**:
   ```bash
   docker compose up -d postgres redis s3 mail
   ```

3. **Запуск миграций**:
   ```bash
   docker compose run --rm migrate
   ```

4. **Запуск сервиса**:
   ```bash
   docker compose up -d account
   ```

После этого сервис будет доступен по адресу `http://localhost:8089`.

## Операционные задачи

### Миграции базы данных
Миграции управляются Flyway. Для ручного запуска:
```bash
java -jar app.jar migrate
```

### Валидация конфигурации
Чтобы проверить, все ли обязательные переменные окружения установлены:
```bash
java -jar app.jar config validate
```

### Проверка работоспособности (Health Checks)
- **HTTP**: `GET http://localhost:8081/readyz`
- **gRPC**: Стандартный сервис проверки здоровья gRPC доступен на порту `9097`.
