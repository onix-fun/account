# SigNoz observability

This directory contains the complete self-hosted observability runtime:

- SigNoz UI, alerting and service analytics;
- SigNoz OTel Collector and managed ClickHouse schema;
- pinned ClickHouse and ZooKeeper configuration;
- native SigNoz dashboard templates.

Application telemetry first reaches the edge collector in
`observability/otel-collector`. The edge collector enriches resources, gathers
infrastructure metrics and file logs, applies tail sampling, and forwards OTLP
to the SigNoz collector. Only the SigNoz collector writes to ClickHouse.

## First start

Open `http://localhost:3301`, create the first administrator, then create an API
key in SigNoz Settings. Import the curated dashboards:

```sh
SIGNOZ_API_KEY=replace-me ./observability/signoz/import-dashboards.sh
```

The templates cover JVM, NGINX, PostgreSQL, Redis, host, ClickHouse and frontend
Web Vitals metrics. SigNoz built-in APM, logs, traces and service dashboards do
not require import.

## Retention and operations

Configure retention in SigNoz Settings rather than changing ClickHouse tables
manually. For a small single-node installation, start with:

- traces: 14 days;
- logs: 30 days;
- metrics: 90 days.

Back up both `signoz_clickhouse_data` and `signoz_sqlite_data`. ClickHouse holds
telemetry; SQLite holds SigNoz users, dashboards, alert rules and settings.

The Compose stack is a single-node reference deployment, not an HA topology.
For Kubernetes use the pinned official Helm chart described in
`examples/kubernetes/README.md`.

Verify health and end-to-end OTLP ingestion at any time:

```sh
./observability/signoz/smoke-test.sh
```
