#!/bin/sh

set -eu

: "${CI_DB_HOST:?CI_DB_HOST is required}"
: "${CI_DB_PORT:?CI_DB_PORT is required}"
: "${CI_DB_NAME:?CI_DB_NAME is required}"
: "${CI_DB_USER:?CI_DB_USER is required}"
: "${CI_MIGRATION_DIR:?CI_MIGRATION_DIR is required}"

if [ ! -d "${CI_MIGRATION_DIR}" ]; then
    echo "Migration directory does not exist: ${CI_MIGRATION_DIR}"
    exit 1
fi

attempt=0
until pg_isready \
    --host="${CI_DB_HOST}" \
    --port="${CI_DB_PORT}" \
    --username="${CI_DB_USER}" \
    --dbname="${CI_DB_NAME}" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "${attempt}" -ge 60 ]; then
        echo "PostgreSQL did not become ready within 60 seconds."
        exit 1
    fi
    sleep 1
done

temp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${temp_dir}"
    unset PGPASSWORD
}
trap cleanup EXIT HUP INT TERM

if [ -n "${CI_DB_PASSWORD:-}" ]; then
    export PGPASSWORD="${CI_DB_PASSWORD}"
fi

migration_list="${temp_dir}/migrations.txt"
find "${CI_MIGRATION_DIR}" \
    -maxdepth 1 \
    -type f \
    -name 'V*__*.sql' \
    -print | sort -V >"${migration_list}"

if [ ! -s "${migration_list}" ]; then
    echo "No versioned SQL migrations were found."
    exit 1
fi

while IFS= read -r migration_file; do
    rendered_file="${temp_dir}/$(basename "${migration_file}")"

    sed \
        -e 's/${backendRuntimeRole}/postgres/g' \
        -e 's/${yjsRuntimeRole}/postgres/g' \
        "${migration_file}" >"${rendered_file}"

    echo "Applying $(basename "${migration_file}") to the disposable CI database."
    psql \
        --host="${CI_DB_HOST}" \
        --port="${CI_DB_PORT}" \
        --username="${CI_DB_USER}" \
        --dbname="${CI_DB_NAME}" \
        --set=ON_ERROR_STOP=1 \
        --file="${rendered_file}" >/dev/null
done <"${migration_list}"

echo "Disposable CI database schema is ready."
