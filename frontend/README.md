# Account Frontend

Нейтральная Vue SPA для регистрации, входа, восстановления доступа, профиля и
управления сессиями.

Один и тот же образ конфигурируется при запуске:

| Переменная | Значение по умолчанию |
|---|---|
| `ACCOUNT_API_BASE_URL` | `/api` |
| `ACCOUNT_FRONTEND_BASE_PATH` | `/` |
| `ACCOUNT_TRUSTED_REDIRECT_ORIGINS` | пустой exact-origin allowlist |

Frontend не содержит branding API и не зависит от API других приложений.
Assets собираются с относительными URL, поэтому образ работает в `/`,
`/account/` и другом prefix без пересборки.

```sh
npm ci
npm test -- --run
npm run build
docker build -t account-frontend .
```
