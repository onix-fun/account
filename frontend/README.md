# Account Frontend

Vue 3 + TypeScript SPA для авторизации и управления аккаунтом.

## Возможности

- пошаговый вход по email или username;
- нейтральный lookup по email без раскрытия аккаунта;
- регистрация, подтверждение email и восстановление доступа;
- шестизначный OTP-компонент с inline-ошибками;
- frontend-валидация до отправки формы;
- профиль, смена email, аватар и управление сессиями;
- локализация RU/EN;
- адаптивный интерфейс для desktop и mobile.

## Архитектура

| Директория | Назначение |
|---|---|
| `src/features/` | auth, profile, avatar и sessions |
| `src/api/` | Axios-клиенты, CSRF/session retry и парсер `ApiError` |
| `src/shared/` | UI, i18n и валидаторы |
| `src/infra/` | store и navigation |
| `src/domain/` | доменные типы |

Frontend не показывает диагностический `message` backend. Код ошибки
локализуется через `errors.<CODE>` в `ru.json` и `en.json`.

## Локальный запуск

Полный стек из корня:

```sh
make up
```

Только frontend:

```sh
npm ci
npm run dev
```

Vite работает на `http://localhost:5174` и проксирует `/api` на
`http://localhost:8089`.

## Команды

```sh
npm run dev
npm test -- --run
npm run build
npm run preview
```

## API и безопасность

- Browser cookies отправляются с `withCredentials`.
- Перед небезопасными запросами клиент получает и передаёт CSRF-токен.
- При истёкшей browser-сессии выполняется один refresh и повтор запроса.
- Network, timeout, `5xx` и неизвестные ошибки получают безопасный fallback.
- При наличии `requestId` он показывается пользователю для обращения в
  поддержку.

Опциональные переменные сборки:

| Переменная | Назначение |
|---|---|
| `VITE_APP_API_URL` / `VITE_API_URL` | базовый путь внешних API; по умолчанию `/api` |
| `VITE_CONTACTS_WS_URL` | явный URL WebSocket |

В production SPA должна обслуживаться через gateway на одном origin с API.
