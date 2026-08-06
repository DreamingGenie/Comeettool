#!/bin/sh
set -eu

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${FLYWAY_USER:?FLYWAY_USER is required}"
: "${FLYWAY_PASSWORD:?FLYWAY_PASSWORD is required}"
: "${FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE:?FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE is required}"
: "${FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE:?FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE is required}"
: "${FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE:?FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE is required}"

fail_validation() {
    echo "$1 is invalid" >&2
    exit 64
}

validate_dns_name() {
    case "$1" in
        ''|*[!A-Za-z0-9.-]*) fail_validation "$2" ;;
    esac
}

validate_port() {
    case "$1" in
        ''|*[!0-9]*) fail_validation "$2" ;;
    esac

    if [ "$1" -lt 1 ] || [ "$1" -gt 65535 ]; then
        fail_validation "$2"
    fi
}

validate_identifier() {
    case "$1" in
        ''|*[!A-Za-z0-9_]*) fail_validation "$2" ;;
    esac

    case "$1" in
        [A-Za-z_]*) ;;
        *) fail_validation "$2" ;;
    esac
}

validate_dns_name "$DB_HOST" "DB_HOST"
validate_port "$DB_PORT" "DB_PORT"
validate_identifier "$DB_NAME" "DB_NAME"
validate_identifier \
    "$FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE" \
    "FLYWAY_PLACEHOLDERS_BACKENDRUNTIMEROLE"
validate_identifier \
    "$FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE" \
    "FLYWAY_PLACEHOLDERS_YJSRUNTIMEROLE"
validate_identifier \
    "$FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE" \
    "FLYWAY_PLACEHOLDERS_AIRUNTIMEROLE"

export FLYWAY_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=verify-full&sslrootcert=/flyway/certs/rds-ca-bundle.pem"

exec flyway "$@"
