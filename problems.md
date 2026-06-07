**Общая оценка**

- Архитектура: **7/10**
- Работоспособность: **6/10**
- Безопасность текущей версии: **5/10**
- Готовность к production: **4/10**

Основные пользовательские сценарии работают, но выпускать систему в production сейчас рискованно.

**Критические проблемы**

1. **Уязвимые Docker-образы**
    - Gateway: **2 CRITICAL + 33 HIGH**.
    - Backend: **16 HIGH**, включая 7 уязвимостей Netty: request smuggling и HTTP/2 DoS.
    - Причина: устаревшие версии Ktor/Netty и базовых образов, плавающие теги контейнеров и отсутствие автоматического контроля уязвимостей.
    - Решение:
        1. Обновить Ktor/Netty и базовые образы до поддерживаемых версий без известных CRITICAL/HIGH уязвимостей.
        2. Закрепить версии всех production-образов и отказаться от тегов `latest`.
        3. Добавить Trivy-проверку зависимостей и собранных образов в pull request и release workflow.
        4. Блокировать релиз при CRITICAL/HIGH; исключения хранить во временном allowlist с причиной, владельцем и сроком действия.
    - Критерий готовности: Trivy не находит незарегистрированных CRITICAL/HIGH уязвимостей, а CI запрещает публикацию небезопасного образа.

2. **Релизы создаются без тестов**
    - Backend собирается с `-DskipTests`.
    - Gateway-тесты вообще не запускаются.
    - Нет CI с тестами для pull request.
    - [release.yml](/Users/docup/Projects/Unlim/account/.github/workflows/release.yml:82)
    - Причина: release workflow сразу собирает и публикует образы, не имея обязательного общего этапа проверки.
    - Решение:
        1. Добавить обязательный pull request workflow: frontend test/build, backend test/package и gateway Lua-тесты.
        2. Убрать `-DskipTests` из backend release job.
        3. Перед публикацией каждого frontend/backend/gateway образа запускать тот же набор проверок.
        4. Публиковать образ только после успешного завершения всех обязательных тестов.
    - Критерий готовности: намеренно сломанный тест блокирует pull request и публикацию любого затронутого release-образа.

3. **Backend integration-тесты сломаны**
    - Из 7 тестов: 3 прошли, 4 завершились ошибкой.
    - Причина: миграция использует PostgreSQL `uuidv7()`, который не поддерживается тестовой H2.
    - [V1__init_schema.sql](/Users/docup/Projects/Unlim/account/backend/src/main/resources/db/migration/V1__init_schema.sql:2)
    - Причина: integration-тесты запускаются на H2, хотя production-схема и SQL используют возможности PostgreSQL.
    - Решение:
        1. Заменить H2 в integration-тестах на PostgreSQL 18 через Testcontainers.
        2. Запускать в тестах настоящие Flyway-миграции без отдельных H2-совместимых веток.
        3. Подключить эти integration-тесты к обязательному pull request и release workflow.
    - Критерий готовности: все backend integration-тесты стабильно проходят локально и в CI на чистом контейнере PostgreSQL 18.

**Высокий риск**

4. **Публичное определение существования аккаунтов**
    - `account-lookup` возвращает `NOT_FOUND`, `ACTIVE`, `BLOCKED`, статус регистрации и URL аватара.
    - Проверено реальным запросом: существующий и несуществующий аккаунты легко различаются.
    - Это позволяет массово собирать список пользователей.
    - [AuthService.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/auth/AuthService.kt:321)

5. **Небезопасные production-значения по умолчанию**
    - PostgreSQL пароль: `account`.
    - MinIO пароль: `account-minio-secret`.
    - Redis запускается вообще без пароля.
    - [docker-compose.yml](/Users/docup/Projects/Unlim/account/examples/docker-compose/docker-compose.yml:47)

6. **Удаление аккаунта не удаляет аватар**
    - Запись пользователя удаляется, но файл остаётся в публичном MinIO bucket.
    - Это проблема приватности и удаления персональных данных.
    - [AuthService.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/auth/AuthService.kt:201)

**Средний риск и недостатки**

7. **Время жизни регистрационного кода не соответствует интерфейсу**
    - API сообщает `900` секунд.
    - Реальный Redis TTL по умолчанию составляет `3600` секунд.
    - [application.yaml](/Users/docup/Projects/Unlim/account/backend/src/main/resources/application.yaml:31)

8. **Нет backend-валидации полей профиля**
    - Имя длиннее 100 символов вызывает реальный `500 Internal Server Error`.
    - Это было подтверждено тестовым запросом.
    - [UserService.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/users/UserService.kt:18)

9. **Проверка попыток регистрационного кода не атомарна**
    - Параллельные запросы могут обойти лимит попыток.
    - [PendingRegistrationStore.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/infrastructure/redis/PendingRegistrationStore.kt:127)

10. **Возможен DoS конкретного аккаунта**
    - Пять неправильных паролей блокируют вход аккаунта на 15 минут.
    - Распределённая атака сможет постоянно удерживать пользователя заблокированным.
    - [AuthService.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/auth/AuthService.kt:221)

11. **Health checks ничего реально не проверяют**
    - Gateway всегда отвечает `200`.
    - Backend не проверяет PostgreSQL, Redis, MinIO и SMTP.
    - [App.kt](/Users/docup/Projects/Unlim/account/backend/src/main/kotlin/profile/App.kt:372)

12. **OpenAPI публично доступен даже при отключённом Swagger**
    - Спецификация раскрывает `/internal/session-check` и полную структуру API.
    - [nginx.conf.template](/Users/docup/Projects/Unlim/account/gateway/nginx.conf.template:121)

13. **Gateway-тесты сломаны**
    - `session_status` использует старые не-UUID идентификаторы.
    - RS256-тест не запускается в текущем окружении образа.
    - [test_session_status.lua](/Users/docup/Projects/Unlim/account/gateway/tests/test_session_status.lua:25)

**Что работает хорошо**

- Argon2id для паролей.
- RS256 JWT.
- Access-токены привязаны к активным сессиям.
- Отзыв сессий после сброса пароля работает.
- HttpOnly/SameSite cookies.
- CSRF и точная проверка Origin.
- CORS корректно отклоняет посторонние origins.
- Токены в query string запрещены.
- Ограничение и обработка аватаров.
- Контейнеры backend и gateway работают не от root.
- Kubernetes NetworkPolicy присутствуют.
- `npm audit`: **0 уязвимостей**.

**Проверенные сценарии**

Полностью прошли:

- регистрация;
- подтверждение email-кода;
- вход;
- обновление профиля;
- получение списка сессий;
- восстановление пароля;
- отзыв старой сессии;
- повторный вход;
- удаление тестового аккаунта;
- CSRF, CORS и запрет query-токенов;
- frontend build и 2 frontend-теста.

Итог: фундамент безопасности неплохой, но сейчас систему нельзя считать безопасной для production из-за уязвимых образов, отсутствия обязательных тестов перед релизом, слабых production-дефолтов и утечки информации об аккаунтах.
