#!/usr/bin/env bash
# Lightweight checks for scripts/poll_compliance_job.sh quoting/validation.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${ROOT}/scripts/poll_compliance_job.sh"
chmod +x "${SCRIPT}"

set +e
OUT="$("${SCRIPT}" 2>&1)"
STATUS=$?
set -e
[[ ${STATUS} -eq 2 ]] || { echo "expected exit 2 for missing job id, got ${STATUS}: ${OUT}"; exit 1; }

set +e
OUT="$("${SCRIPT}" not-a-uuid 2>&1)"
STATUS=$?
set -e
[[ ${STATUS} -eq 2 ]] || { echo "expected exit 2 for invalid uuid, got ${STATUS}: ${OUT}"; exit 1; }
grep -qi "uuid" <<<"${OUT}" || { echo "expected uuid validation message: ${OUT}"; exit 1; }

echo "poll_compliance_job.sh validation checks passed"
