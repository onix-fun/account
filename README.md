# Account

Account owns authentication, sessions, users and owner identities, organizations and membership,
the social graph, privacy/access decisions, and the notification inbox. Public profile data and
avatars are owned by Profile and Media; Account proxies those commands for its SPA.

## Structure

- `api/proto` — canonical Account gRPC contract.
- `backend/{domain,application,infrastructure,app}` — Kotlin 2 / Ktor clean modules.
- `frontend/src/{app,pages,features,shared}` — Vue 3 feature-based SPA.
- `.contracts` — ignored contracts synchronized from Profile and Media by `onix-dev`.

The backend uses packages under `com.onix.account`. Runtime configuration is provided only through
`ACCOUNT_*` variables; secrets also support the matching `*_FILE` form.

## Validation

```sh
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) gradle --no-daemon test checkModuleBoundaries

cd ../frontend
npm ci
npm test
npm run build
```

Local topology, key generation, contract synchronization, database reset, and cross-service smoke
tests live in the separate `onix-dev` repository. See `../docs/` when this repository is checked out
inside the development workspace.
