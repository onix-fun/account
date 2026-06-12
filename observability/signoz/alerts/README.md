# Recommended SigNoz alerts

Create these rules after the first administrator setup. Route notifications
through a SigNoz notification channel configured from secrets.

| Alert | Condition | Window |
|---|---|---|
| API availability | successful requests below 99.9% | 10m |
| API server errors | HTTP 5xx above 1% | 5m |
| API latency | p95 above 500ms | 10m |
| Dependency unavailable | PostgreSQL, Redis, MinIO or ClickHouse scrape absent | 5m |
| Email outbox backlog | pending/failed outbox messages above expected baseline | 10m |
| Frontend errors | `frontend.errors` exceeds baseline | 10m |
| Web Vitals regression | LCP > 2.5s, INP > 200ms or CLS > 0.1 | 15m |
| ClickHouse capacity | disk usage above 80% | 15m |
| Collector delivery | failed or refused OTel exports above zero | 5m |

Use separate warning and critical notification policies. Every alert should
include a service owner, dashboard link and a short remediation runbook.
