#!/usr/bin/env bash
# Logical dump of production/staging PostgreSQL before V19–V26 migration.
# Usage: scripts/backup-prod-db.sh /explicit/backup/directory [dump-basename]
set -euo pipefail
umask 077

if [[ $# -lt 1 || $# -gt 2 ]]; then
  printf 'Usage: %s /explicit/backup/directory [dump-basename]\n' "$0" >&2
  exit 64
fi

backup_root="$1"
case "$backup_root" in
  /|"$HOME"|".") printf 'Refusing broad backup target: %s\n' "$backup_root" >&2; exit 64 ;;
esac

: "${DATABASE_URL:?DATABASE_URL is required}"

basename="${2:-pre_v19_v26_$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$backup_root"
outfile="${backup_root%/}/${basename}.dump"

# Never echo DATABASE_URL (may contain credentials).
pg_dump --format=custom --no-owner --no-acl --dbname="$DATABASE_URL" --file="$outfile"

if [[ ! -s "$outfile" ]]; then
  printf 'error: backup file missing or empty: %s\n' "$outfile" >&2
  exit 1
fi

sha256sum "$outfile" > "${outfile}.sha256"
printf 'Backup OK bytes=%s file=%s\n' "$(wc -c < "$outfile" | tr -d ' ')" "$outfile"
