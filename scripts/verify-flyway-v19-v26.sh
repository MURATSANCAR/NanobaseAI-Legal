#!/usr/bin/env bash
# Verify Flyway V19–V26 success on the target database.
# Usage: scripts/verify-flyway-v19-v26.sh
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -At <<'SQL' > /tmp/flyway-v19-v26.txt
SELECT version || '|' || success
  FROM flyway_schema_history
 WHERE version IN ('19','20','21','22','23','24','25','26')
 ORDER BY installed_rank;
SQL

expected=(19 20 21 22 23 24 25 26)
missing=0
for version in "${expected[@]}"; do
  if ! grep -qx "${version}|t" /tmp/flyway-v19-v26.txt && ! grep -qx "${version}|true" /tmp/flyway-v19-v26.txt; then
    printf 'FAIL: migration V%s not SUCCESS\n' "$version" >&2
    missing=1
  else
    printf 'OK: V%s SUCCESS\n' "$version"
  fi
done

if [[ "$missing" -ne 0 ]]; then
  printf 'Flyway history dump:\n' >&2
  cat /tmp/flyway-v19-v26.txt >&2 || true
  exit 1
fi

printf 'All V19–V26 migrations SUCCESS\n'
