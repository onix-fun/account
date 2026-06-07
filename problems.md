**Общая оценка**

- Архитектура: **7/10**
- Работоспособность: **6/10**
- Безопасность текущей версии: **5/10**
- Готовность к production: **4/10**

Основные пользовательские сценарии работают, но выпускать систему в production сейчас рискованно.

**Критические проблемы**

🔴 Уровень 2 — Race Conditions / Data Integrity
4. TOCTOU race condition при регистрации AuthService.confirmRegistration() вызывает ensureUserCanBeCreated(), затем userRepository.create(). При REPEATABLE READ isolation два конкурентных запроса с одинаковым email увидят snapshot без этого email и оба попытаются выполнить INSERT. UserRepository.create() (UserRepository.kt:52-72) не обрабатывает SQLException с sqlState == "23505" (unique violation). Результат: пользователь получает HTTP 500 вместо внятной ошибки.

5. Race condition в сбросе пароля VerificationTokenRepository.verify() использует SELECT ... FOR UPDATE с LIMIT 1 без ORDER BY. При конкурентных запросах сброса могут быть выбраны разные токены. Успешная верификация одного токена не блокирует остальные. Потенциально два разных кода могут сбросить пароль дважды (хотя invalidateAll и revokeAllForUser смягчают последствия).

6. Redis cache invalidation при удалении аккаунта AuthService.deleteAccount() (AuthService.kt:205-212) вызывает только userRepository.delete(userId). Не вызывает revokeCachedSessions(userId) — Redis-кэш сессий не очищается. Если access token ещё жив (до 15 мин), gateway найдёт сессию в Redis и пропустит запрос (хотя _internal/session-check проверит статус пользователя как fallback).

7. Gateway Redis + Backend Redis — могут быть разными gateway/lua/session_status.lua:15 читает IDENTITY_REDIS_HOST, а бэкенд читает redis.url из application.yaml. Если gateway смотрит в другой Redis, отзыв сессии через бэкенд не дойдёт до gateway → сессия останется валидной до expiry.

🔴 Уровень 3 — Логические ошибки
8. Gateway session_status.lua: соединение без пула session_status.lua:13-15 создаёт новое TCP-соединение к Redis на каждый запрос. Нет повторного использования (pooling). При высокой нагрузке это приведёт к исчерпанию сокетов и падению gateway.

9. Rate limit plugin не отличает пользователей RateLimitPlugin.kt:35-37 использует X-Real-IP как ключ rate limit'а. Если несколько пользователей за одним NAT, все будут заблокированы из-за одного. И наоборот — атакующий с разных IP может делать по 20 запросов с каждого.

10. Обработка X-Real-IP без верификации AuthController.clientIpAddress() и RateLimitPlugin доверяют заголовку X-Real-IP. При прямом доступе к бэкенду (минуя gateway) клиент может подделать IP и обойти rate limiting.

11. pending_email_changes.new_email — UNIQUE без учета expires_at В схеме V1__init_schema.sql:65: new_email TEXT NOT NULL UNIQUE. Если пользователь A запросил смену на x@y.com, пользователь B не сможет запросить ту же почту, пока запись не протухнет или не будет отменена. При этом висит expires_at, но уникальность не учитывает срок действия.
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
