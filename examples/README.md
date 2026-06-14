# Deployment examples

- `docker-compose/` запускает опубликованные frontend/backend образы с внешним nginx.
- `kubernetes/` содержит deployments, migration Job, Services и Ingress.

Reverse proxy должен направлять `/api/*`, `/.well-known/jwks.json`,
`/openapi.json`, `/swagger-ui`, `/livez`, `/readyz` и `/metrics` в backend,
а остальные HTTP-запросы во frontend.
