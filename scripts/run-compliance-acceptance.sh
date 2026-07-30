#!/usr/bin/env bash
# Sequential compliance acceptance runner (does not start jobs in parallel).
# Usage:
#   ORG_ID=... PROJECT_ID=... TOKEN=... scripts/run-compliance-acceptance.sh
# Optional:
#   API=http://127.0.0.1:8080
#   RUNS=3
#   POLL_SCRIPT=scripts/poll_compliance_job.sh
set -euo pipefail

: "${ORG_ID:?ORG_ID is required}"
: "${PROJECT_ID:?PROJECT_ID is required}"
: "${TOKEN:?TOKEN is required}"

API="${API:-http://127.0.0.1:8080}"
RUNS="${RUNS:-3}"
POLL_SCRIPT="${POLL_SCRIPT:-scripts/poll_compliance_job.sh}"
REPORT_DIR="${REPORT_DIR:-./release/2026-07-31}"
mkdir -p "$REPORT_DIR"

if ! [[ "$ORG_ID" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "error: ORG_ID must be a UUID" >&2
  exit 2
fi
if ! [[ "$PROJECT_ID" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "error: PROJECT_ID must be a UUID" >&2
  exit 2
fi
if [[ ! -f "$POLL_SCRIPT" ]]; then
  echo "error: poll script missing: $POLL_SCRIPT" >&2
  exit 1
fi

report="${REPORT_DIR}/acceptance-jobs-report.txt"
: > "$report"
printf 'acceptance_runs=%s started_at=%s\n' "$RUNS" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$report"

for ((i=1; i<=RUNS; i++)); do
  printf 'Starting acceptance job %s/%s\n' "$i" "$RUNS"
  response="$(curl -fsS -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "X-Organization-Id: ${ORG_ID}" \
    "${API}/api/v1/projects/${PROJECT_ID}/compliance-analyses" \
    -d '{}')"
  job_id="$(printf '%s' "$response" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F-]\{36\}\)".*/\1/p' | head -1)"
  if [[ -z "$job_id" ]]; then
    job_id="$(printf '%s' "$response" | sed -n 's/.*"jobId"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F-]\{36\}\)".*/\1/p' | head -1)"
  fi
  if [[ -z "$job_id" ]]; then
    echo "error: could not parse job id from response" >&2
    printf '%s\n' "$response" >&2
    exit 1
  fi
  printf 'job_%s id=%s\n' "$i" "$job_id" >> "$report"
  ORG_ID="$ORG_ID" bash "$POLL_SCRIPT" "$job_id"
  printf 'job_%s completed_at=%s\n' "$i" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$report"
done

printf 'All %s acceptance jobs finished\n' "$RUNS"
printf 'report=%s\n' "$report"
