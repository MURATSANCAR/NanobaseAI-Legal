#!/usr/bin/env bash
# Cross-tenant read isolation smoke checks.
# Usage:
#   TOKEN_A=... TOKEN_B=... ORG_A=... ORG_B=... \
#   TENDER_B=... CAPABILITY_B=... GAP_B=... \
#   scripts/verify-tenant-isolation.sh
set -euo pipefail

: "${TOKEN_A:?TOKEN_A is required}"
: "${ORG_A:?ORG_A is required}"
: "${ORG_B:?ORG_B is required}"
: "${TENDER_B:?TENDER_B (ORG_B project id) is required}"

API="${API:-http://127.0.0.1:8080}"
REPORT_DIR="${REPORT_DIR:-./release/2026-07-31}"
mkdir -p "$REPORT_DIR"
report="${REPORT_DIR}/tenant-isolation-report.txt"
: > "$report"

for id_name in ORG_A ORG_B TENDER_B; do
  value="${!id_name}"
  if ! [[ "$value" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    echo "error: $id_name must be a UUID" >&2
    exit 2
  fi
done

check_denied() {
  local label="$1"
  local url="$2"
  local code
  code="$(curl -sS -o /tmp/tenant-body.txt -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN_A}" \
    -H "X-Organization-Id: ${ORG_A}" \
    "$url" || true)"
  printf '%s http=%s\n' "$label" "$code" | tee -a "$report"
  if [[ "$code" != "403" && "$code" != "404" ]]; then
    echo "FAIL: expected 403/404 for $label, got $code" >&2
    head -c 500 /tmp/tenant-body.txt >&2 || true
    exit 1
  fi
  if grep -Eqi "$ORG_B|$TENDER_B" /tmp/tenant-body.txt; then
    echo "FAIL: cross-tenant identifiers leaked in body for $label" >&2
    exit 1
  fi
}

check_denied "tender_read" "${API}/api/v1/projects/${TENDER_B}"

if [[ -n "${CAPABILITY_B:-}" ]]; then
  check_denied "capability_read" "${API}/api/v1/company-capabilities/${CAPABILITY_B}"
fi
if [[ -n "${GAP_B:-}" ]]; then
  check_denied "gap_read" "${API}/api/v1/projects/${TENDER_B}/gaps"
fi
if [[ -n "${SUMMARY_B:-}" || -n "${TENDER_B:-}" ]]; then
  check_denied "summary_read" "${API}/api/v1/projects/${TENDER_B}/assessment-summary"
fi

printf 'tenant_isolation PASS\n' | tee -a "$report"
