#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s /explicit/backup/output-directory\n' "$0" >&2
  exit 64
fi
backup_root="$1"
case "$backup_root" in
  /|"$HOME"|".") printf 'Refusing broad backup target: %s\n' "$backup_root" >&2; exit 64 ;;
esac

: "${DATABASE_URL:?DATABASE_URL is required}"
: "${DATABASE_USER:?DATABASE_USER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${MINIO_ALIAS:?MINIO_ALIAS is required}"
: "${MINIO_BUCKET:?MINIO_BUCKET is required}"
: "${AGE_RECIPIENT:?AGE_RECIPIENT is required}"
: "${KEYCLOAK_EXPORT_FILE:?KEYCLOAK_EXPORT_FILE is required}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="${backup_root%/}/specai-${timestamp}"
mkdir -p "$run_dir/minio"

pg_dump --format=custom --no-owner --no-acl \
  --dbname="$DATABASE_URL" --username="$DATABASE_USER" \
  --file="$run_dir/postgresql.dump"
mc mirror --overwrite "${MINIO_ALIAS}/${MINIO_BUCKET}" "$run_dir/minio"
cp "$KEYCLOAK_EXPORT_FILE" "$run_dir/keycloak-realm.json"

(
  cd "$run_dir"
  find . -type f ! -name manifest.sha256 -print0 \
    | sort -z | xargs -0 sha256sum > manifest.sha256
)
tar -C "$run_dir" -czf - . | age --recipient "$AGE_RECIPIENT" \
  --output="${run_dir}.tar.gz.age"
sha256sum "${run_dir}.tar.gz.age" > "${run_dir}.tar.gz.age.sha256"
printf 'Encrypted backup created: %s\n' "${run_dir}.tar.gz.age"
