#!/bin/sh
set -eu

: "${ACCOUNT_TRUSTED_PROXY_CIDRS:=127.0.0.1/32}"
: "${ACCOUNT_DNS_RESOLVER:=127.0.0.11}"
: "${ACCOUNT_LOGIN_RATE:=5r/m}"
: "${ACCOUNT_RECOVERY_RATE:=3r/m}"
: "${ACCOUNT_CONFIRMATION_RATE:=10r/m}"
: "${ACCOUNT_USERNAME_RATE:=20r/m}"
: "${ACCOUNT_SESSION_RATE:=30r/m}"
: "${ACCOUNT_AVATAR_RATE:=10r/m}"
: "${ACCOUNT_CSP:=default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self' http://localhost:8088 ws://localhost:8088; object-src 'none'; base-uri 'none'; frame-ancestors 'none'}"
: "${ACCOUNT_HSTS_HEADER:=}"
: "${APP_ENV:=development}"
: "${ACCOUNT_ALLOWED_ORIGINS:=}"
: "${IDENTITY_INTERNAL_AUTH_SECRET:=}"

if [ "$APP_ENV" = "production" ]; then
  [ -n "$ACCOUNT_ALLOWED_ORIGINS" ] || { echo "ACCOUNT_ALLOWED_ORIGINS is required"; exit 1; }
  [ -n "$ACCOUNT_HSTS_HEADER" ] || { echo "ACCOUNT_HSTS_HEADER is required"; exit 1; }
  [ -n "$ACCOUNT_TRUSTED_PROXY_CIDRS" ] || { echo "ACCOUNT_TRUSTED_PROXY_CIDRS is required"; exit 1; }
  [ "${#IDENTITY_INTERNAL_AUTH_SECRET}" -ge 32 ] || { echo "IDENTITY_INTERNAL_AUTH_SECRET must be at least 32 characters"; exit 1; }
fi

trusted_proxy_config=/usr/local/openresty/nginx/conf/trusted-proxies.conf
: > "$trusted_proxy_config"
old_ifs=$IFS
IFS=,
for cidr in $ACCOUNT_TRUSTED_PROXY_CIDRS; do
    printf 'set_real_ip_from %s;\n' "$cidr" >> "$trusted_proxy_config"
done
IFS=$old_ifs

export ACCOUNT_LOGIN_RATE
export ACCOUNT_RECOVERY_RATE
export ACCOUNT_CONFIRMATION_RATE
export ACCOUNT_USERNAME_RATE
export ACCOUNT_SESSION_RATE
export ACCOUNT_AVATAR_RATE
export ACCOUNT_CSP
export ACCOUNT_HSTS_HEADER
export ACCOUNT_DNS_RESOLVER
export APP_ENV

envsubst '${ACCOUNT_LOGIN_RATE} ${ACCOUNT_RECOVERY_RATE} ${ACCOUNT_CONFIRMATION_RATE} ${ACCOUNT_USERNAME_RATE} ${ACCOUNT_SESSION_RATE} ${ACCOUNT_AVATAR_RATE} ${ACCOUNT_CSP} ${ACCOUNT_HSTS_HEADER} ${ACCOUNT_DNS_RESOLVER} ${IDENTITY_INTERNAL_AUTH_SECRET}' \
    < /usr/local/openresty/nginx/conf/nginx.conf.template \
    > /usr/local/openresty/nginx/conf/nginx.conf

exec openresty -g 'daemon off;'
