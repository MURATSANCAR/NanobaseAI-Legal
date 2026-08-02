#!/usr/bin/env bash
# Wait for RUN_2A exact HBYS parser acceptance PASS, then auto-start RUN_2B full E2E.
set -euo pipefail

ROOT="${HBYS_E2E_ROOT:-/tmp/nanobase-hbys-public-e2e}"
DIAG="${ROOT}/run-2a-diagnostic"
DI_DIAG_IN_CONTAINER="/var/lib/specai/run-2a-diagnostic"
JOB_ID="${RUN2A_PARSER_JOB_ID:-130c7f09-8276-4314-850f-2b923ad0651a}"
EXPECTED_PAGES="${HBYS_EXPECTED_PAGES:-235}"
LEGAL_DIR="${LEGAL_DIR:-/data/nanobaseai/legal}"
LOG="${DIAG}/autochain.log"
LOCK="${DIAG}/autochain.lock"
STATUS_JSON="${DIAG}/autochain-status.json"
RUN2B_SCRIPT="${LEGAL_DIR}/scripts/public_hbys_blind_e2e_run2b.py"

mkdir -p "$DIAG" "${ROOT}/run-2b-post-fix" "${ROOT}/run-2b-review-bundle"

log() { echo "$(date -Is) $*" | tee -a "$LOG"; }

write_status() {
  python3 - "$STATUS_JSON" "$@" <<'PY'
import json,sys
path=sys.argv[1]
payload=json.loads(sys.argv[2])
open(path,"w").write(json.dumps(payload,indent=2,ensure_ascii=False)+"\n")
PY
}

if [[ -f "$LOCK" ]]; then
  old_pid=$(cat "$LOCK" 2>/dev/null || true)
  if [[ -n "${old_pid}" ]] && kill -0 "$old_pid" 2>/dev/null; then
    log "autochain already running pid=$old_pid"
    exit 0
  fi
fi
echo $$ > "$LOCK"
trap 'rm -f "$LOCK"' EXIT

write_status "{\"phase\":\"WAIT_RUN2A\",\"parserJobId\":\"$JOB_ID\",\"run2b\":\"NOT_STARTED\"}"

log "autochain started; waiting for RUN_2A job=$JOB_ID"

# Poll until acceptance writes EXIT: or DI job COMPLETED, then validate parser-result.json
while true; do
  # Mirror acceptance artifacts from DI volume when present
  docker cp "specai-legal-document-intelligence-1:${DI_DIAG_IN_CONTAINER}/." "$DIAG/" 2>/dev/null || true

  if [[ -f "$DIAG/acceptance.log" ]] && grep -q '^EXIT:' "$DIAG/acceptance.log"; then
    exit_line=$(grep '^EXIT:' "$DIAG/acceptance.log" | tail -1)
    log "acceptance finished: $exit_line"
    break
  fi

  # Fallback: query DI job terminal
  job_json=$(docker exec specai-legal-document-intelligence-1 python -c \
    "import json,urllib.request; print(json.dumps(json.load(urllib.request.urlopen('http://127.0.0.1:8090/v1/jobs/${JOB_ID}', timeout=10))))" \
    2>/dev/null || echo '{}')
  echo "$job_json" > "$DIAG/live-status.json"
  status=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status') or '')" <<<"$job_json")
  msg=$(python3 -c "import json,sys; d=json.load(sys.stdin); print((d.get('message') or '')[:180])" <<<"$job_json")
  log "wait status=$status msg=$msg"
  if [[ "$status" == "COMPLETED" || "$status" == "FAILED" || "$status" == "CANCELLED" ]]; then
    # give acceptance poller a moment to fetch result
    sleep 20
    docker cp "specai-legal-document-intelligence-1:${DI_DIAG_IN_CONTAINER}/." "$DIAG/" 2>/dev/null || true
    break
  fi
  sleep 60
done

# Gate evaluation
python3 - <<PY
import json, sys
from pathlib import Path
diag = Path("$DIAG")
expected_pages = int("$EXPECTED_PAGES")
result_path = diag / "parser-result.json"
tests_path = diag / "test-results.json"
gate = {
  "run2aParserDiagnostic": "FAIL",
  "reasons": [],
  "parserResult": None,
  "tests": None,
}
if not result_path.is_file():
  # Try to materialize from DI job result if acceptance script failed after COMPLETED
  gate["reasons"].append("parser-result.json missing")
else:
  result = json.loads(result_path.read_text())
  gate["parserResult"] = result
  ok = (
    result.get("acceptance") == "PASS"
    and int(result.get("pageCount") or 0) == expected_pages
    and int(result.get("processedPageCount") or 0) == expected_pages
    and int(result.get("failedPageCount") or 0) == 0
    and int(result.get("layoutBlockCount") or 0) > 0
    and result.get("terminalStatus") == "READY"
    and result.get("qualityGate") == "PASS"
  )
  if not ok:
    gate["reasons"].append(f"parser acceptance gate failed: {result}")
  else:
    gate["run2aParserDiagnostic"] = "PASS"

if tests_path.is_file():
  tests = json.loads(tests_path.read_text())
  gate["tests"] = tests
  unit = tests.get("unitAndIntegration") or tests
  passed = int(unit.get("passed") or 0)
  failed = int(unit.get("failed") or 0)
  skipped = int(unit.get("skipped") or 0)
  if not (passed > 0 and failed == 0 and skipped == 0):
    gate["reasons"].append(f"tests gate failed passed={passed} failed={failed} skipped={skipped}")
    gate["run2aParserDiagnostic"] = "FAIL"
else:
  gate["reasons"].append("test-results.json missing")
  gate["run2aParserDiagnostic"] = "FAIL"

(diag / "run2a-gate.json").write_text(json.dumps(gate, indent=2, ensure_ascii=False) + "\n")
print(json.dumps(gate, ensure_ascii=False))
if gate["run2aParserDiagnostic"] != "PASS":
  sys.exit(10)
PY
GATE_RC=$?

if [[ "$GATE_RC" -ne 0 ]]; then
  write_status "{\"phase\":\"RUN2A_GATE_FAIL\",\"run2b\":\"NOT_STARTED\",\"gateFile\":\"$DIAG/run2a-gate.json\"}"
  log "RUN_2A gate FAIL — RUN_2B NOT_STARTED"
  exit 10
fi

write_status "{\"phase\":\"RUN2B_STARTING\",\"run2a\":\"PASS\",\"run2b\":\"STARTING\"}"
log "RUN_2A PASS — starting RUN_2B"

export SPECAI_API="${SPECAI_API:-http://127.0.0.1:8098}"
export HBYS_E2E_ROOT="$ROOT"
export HBYS_PDF="${HBYS_PDF:-$ROOT/source/hbys-technical-specification.pdf}"
export HBYS_EXPECTED_PAGES="$EXPECTED_PAGES"

# Ensure host diagnostic has mirrored parser-result for review bundle
docker cp "specai-legal-document-intelligence-1:${DI_DIAG_IN_CONTAINER}/." "$DIAG/" 2>/dev/null || true

set +e
python3 "$RUN2B_SCRIPT" > "${ROOT}/run-2b-post-fix/runner.stdout" 2>&1
RC=$?
set -e

write_status "{\"phase\":\"RUN2B_FINISHED\",\"run2a\":\"PASS\",\"run2bExit\":$RC,\"reviewBundle\":\"${ROOT}/run-2b-review-bundle\"}"
log "RUN_2B finished exit=$RC"
exit "$RC"
