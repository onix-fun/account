#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-dev/docker-compose.yml}"
SIGNOZ_URL="${SIGNOZ_URL:-http://localhost:3301}"
OTEL_HTTP_ENDPOINT="${OTEL_HTTP_ENDPOINT:-http://localhost:4318}"
now="$(($(date +%s) * 1000000000))"
end="$((now + 1000000))"
run_id="smoke-$(date +%s)"

curl --fail --silent "$SIGNOZ_URL/api/v1/health" >/dev/null

curl --fail --silent -H "Content-Type: application/json" \
  --data '{"resourceMetrics":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"observability-smoke"}},{"key":"smoke.run_id","value":{"stringValue":"'"$run_id"'"}}]},"scopeMetrics":[{"scope":{"name":"account.smoke"},"metrics":[{"name":"account.observability.smoke","gauge":{"dataPoints":[{"asDouble":1,"timeUnixNano":"'"$now"'"}]}}]}]}]}' \
  "$OTEL_HTTP_ENDPOINT/v1/metrics" >/dev/null

curl --fail --silent -H "Content-Type: application/json" \
  --data '{"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"observability-smoke"}},{"key":"smoke.run_id","value":{"stringValue":"'"$run_id"'"}}]},"scopeLogs":[{"scope":{"name":"account.smoke"},"logRecords":[{"timeUnixNano":"'"$now"'","severityNumber":9,"severityText":"INFO","body":{"stringValue":"observability-ingestion-smoke"}}]}]}]}' \
  "$OTEL_HTTP_ENDPOINT/v1/logs" >/dev/null

curl --fail --silent -H "Content-Type: application/json" \
  --data '{"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"observability-smoke"}},{"key":"smoke.run_id","value":{"stringValue":"'"$run_id"'"}}]},"scopeSpans":[{"scope":{"name":"account.smoke"},"spans":[{"traceId":"1123456789abcdef0123456789abcdef","spanId":"1123456789abcdef","name":"observability-ingestion-smoke","kind":2,"startTimeUnixNano":"'"$now"'","endTimeUnixNano":"'"$end"'","status":{"code":2,"message":"smoke-test"}}]}]}]}' \
  "$OTEL_HTTP_ENDPOINT/v1/traces" >/dev/null

for _ in $(seq 1 30); do
  metric_count="$(docker compose -f "$COMPOSE_FILE" exec -T clickhouse clickhouse-client --query "SELECT count() FROM signoz_metrics.distributed_time_series_v4_1day WHERE metric_name = 'account.observability.smoke' AND resource_attrs['smoke.run_id'] = '$run_id'" 2>/dev/null || true)"
  log_count="$(docker compose -f "$COMPOSE_FILE" exec -T clickhouse clickhouse-client --query "SELECT count() FROM signoz_logs.distributed_logs_v2 WHERE body = 'observability-ingestion-smoke' AND resources_string['smoke.run_id'] = '$run_id'" 2>/dev/null || true)"
  trace_count="$(docker compose -f "$COMPOSE_FILE" exec -T clickhouse clickhouse-client --query "SELECT count() FROM signoz_traces.distributed_signoz_index_v3 WHERE name = 'observability-ingestion-smoke' AND resources_string['smoke.run_id'] = '$run_id'" 2>/dev/null || true)"
  if [ "${metric_count:-0}" -gt 0 ] && [ "${log_count:-0}" -gt 0 ] && [ "${trace_count:-0}" -gt 0 ]; then
    echo "SigNoz ingestion smoke passed: metrics=$metric_count logs=$log_count traces=$trace_count"
    exit 0
  fi
  sleep 2
done

echo "SigNoz ingestion smoke failed: metrics=${metric_count:-0} logs=${log_count:-0} traces=${trace_count:-0}" >&2
exit 1
