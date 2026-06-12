#!/usr/bin/env sh
set -eu

SIGNOZ_URL="${SIGNOZ_URL:-http://localhost:3301}"
DASHBOARD_DIR="${DASHBOARD_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/dashboards" && pwd)}"

if [ -z "${SIGNOZ_API_KEY:-}" ]; then
  echo "SIGNOZ_API_KEY is required. Create an API key in SigNoz Settings." >&2
  exit 1
fi

for dashboard in "$DASHBOARD_DIR"/*.json; do
  title="$(jq -r '.title' "$dashboard")"
  create_response="$(
    curl --fail-with-body --silent --show-error \
      -H "SIGNOZ-API-KEY: $SIGNOZ_API_KEY" \
      -H "Content-Type: application/json" \
      --data "$(jq -cn --arg title "$title" '{title: $title, uploadedGrafana: false}')" \
      "$SIGNOZ_URL/api/v1/dashboards"
  )"
  dashboard_id="$(printf '%s' "$create_response" | jq -er '.data.id // .id')"

  curl --fail-with-body --silent --show-error \
    -X PUT \
    -H "SIGNOZ-API-KEY: $SIGNOZ_API_KEY" \
    -H "Content-Type: application/json" \
    --data-binary "@$dashboard" \
    "$SIGNOZ_URL/api/v1/dashboards/$dashboard_id" >/dev/null

  echo "Imported: $title"
done
