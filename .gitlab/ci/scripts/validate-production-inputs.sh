#!/bin/sh

set -eu

validation_scope="${1:-release}"

fail_validation() {
  echo "Production input validation failed: $1" >&2
  exit 1
}

require_value() {
  validation_name="$1"
  eval "validation_value=\${$validation_name:-}"
  [ -n "$validation_value" ] || fail_validation "$validation_name is not configured."
}

is_https_url() {
  printf '%s' "$1" | grep -Eq '^https://[^[:space:]/]+(:[0-9]+)?(/[^[:space:]]*)?$'
}

is_wss_url() {
  printf '%s' "$1" | grep -Eq '^wss://[^[:space:]/]+(:[0-9]+)?/[^[:space:]]+$'
}

[ "${CI_COMMIT_BRANCH:-}" = "main" ] \
  || fail_validation "deployments are restricted to main."
[ "${CI_COMMIT_REF_PROTECTED:-}" = "true" ] \
  || fail_validation "main must be a protected branch."

require_value CI_COMMIT_SHA
printf '%s' "$CI_COMMIT_SHA" | grep -Eq '^[0-9a-f]{40}$' \
  || fail_validation "CI_COMMIT_SHA is not a full Git commit SHA."

case "$validation_scope" in
  container)
    ;;
  frontend)
    require_value DEPLOY_API_BASE_URL
    require_value DEPLOY_YJS_WEBSOCKET_URL
    is_https_url "$DEPLOY_API_BASE_URL" \
      || fail_validation "DEPLOY_API_BASE_URL must use HTTPS."
    is_wss_url "$DEPLOY_YJS_WEBSOCKET_URL" \
      || fail_validation "DEPLOY_YJS_WEBSOCKET_URL must use WSS."
    case "$DEPLOY_API_BASE_URL" in
      */) fail_validation "DEPLOY_API_BASE_URL must not end with a slash." ;;
    esac
    case "$DEPLOY_YJS_WEBSOCKET_URL" in
      */collaboration) ;;
      *) fail_validation "DEPLOY_YJS_WEBSOCKET_URL must end with /collaboration." ;;
    esac
    ;;
  release)
    require_value DEPLOY_API_BASE_URL
    require_value DEPLOY_YJS_WEBSOCKET_URL
    require_value DEPLOY_FRONTEND_URL
    require_value DEPLOY_FRONTEND_BUCKET
    require_value DEPLOY_CLOUDFRONT_DISTRIBUTION_ID
    is_https_url "$DEPLOY_API_BASE_URL" \
      || fail_validation "DEPLOY_API_BASE_URL must use HTTPS."
    is_https_url "$DEPLOY_FRONTEND_URL" \
      || fail_validation "DEPLOY_FRONTEND_URL must use HTTPS."
    is_wss_url "$DEPLOY_YJS_WEBSOCKET_URL" \
      || fail_validation "DEPLOY_YJS_WEBSOCKET_URL must use WSS."
    case "$DEPLOY_API_BASE_URL" in
      */) fail_validation "DEPLOY_API_BASE_URL must not end with a slash." ;;
    esac
    case "$DEPLOY_FRONTEND_URL" in
      */) fail_validation "DEPLOY_FRONTEND_URL must not end with a slash." ;;
    esac
    case "$DEPLOY_YJS_WEBSOCKET_URL" in
      */collaboration) ;;
      *) fail_validation "DEPLOY_YJS_WEBSOCKET_URL must end with /collaboration." ;;
    esac
    printf '%s' "$DEPLOY_FRONTEND_BUCKET" \
      | grep -Eq '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$' \
      || fail_validation "DEPLOY_FRONTEND_BUCKET is not a valid bucket name."
    printf '%s' "$DEPLOY_CLOUDFRONT_DISTRIBUTION_ID" \
      | grep -Eq '^[A-Z0-9]{8,32}$' \
      || fail_validation "DEPLOY_CLOUDFRONT_DISTRIBUTION_ID is invalid."
    ;;
  *)
    fail_validation "unknown validation scope."
    ;;
esac

echo "Production input contract validated."
