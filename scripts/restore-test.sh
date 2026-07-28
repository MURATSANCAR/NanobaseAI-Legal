#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ "${DEPLOYMENT_PROFILE:-}" != "staging" ]]; then
  printf 'Restore validation is restricted to an empty staging profile.\n' >&2
  exit 64
fi
if [[ $# -ne 2 ]]; then
  printf 'Usage: %s encrypted-backup.age /explicit/work-directory\n' "$0" >&2
  exit 64
fi
archive="$1"
work_dir="$2"
case "$work_dir" in
  /|"$HOME"|".") printf 'Refusing broad restore work directory.\n' >&2; exit 64 ;;
esac
: "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE is required}"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${DATABASE_USER:?DATABASE_USER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${MINIO_ALIAS:?MINIO_ALIAS is required}"
: "${MINIO_BUCKET:?MINIO_BUCKET is required}"
: "${SMOKE_BASE_URL:?SMOKE_BASE_URL is required}"
: "${SMOKE_ACCESS_TOKEN:?SMOKE_ACCESS_TOKEN is required}"

started="$(date +%s)"
mkdir -p "$work_dir"
age --decrypt --identity "$AGE_IDENTITY_FILE" "$archive" | tar -xzf - -C "$work_dir"
(
  cd "$work_dir"
  sha256sum --check manifest.sha256
)
pg_restore --clean --if-exists --no-owner --no-acl \
  --dbname="$DATABASE_URL" --username="$DATABASE_USER" "$work_dir/postgresql.dump"
mc mirror --overwrite "$work_dir/minio" "${MINIO_ALIAS}/${MINIO_BUCKET}"

for path in /actuator/health/readiness /api/v1/tenders /api/v1/audit-events; do
  curl --fail --silent --show-error \
    --header "Authorization: Bearer ${SMOKE_ACCESS_TOKEN}" \
    "${SMOKE_BASE_URL}${path}" >/dev/null
done
elapsed=$(( $(date +%s) - started ))
printf 'Restore validation succeeded in %s seconds.\n' "$elapsed"
