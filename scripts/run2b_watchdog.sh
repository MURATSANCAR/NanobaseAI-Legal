#!/bin/bash
# Active RUN_2B watchdog — intervene on stall/death/unhealthy backend
set -u
ROOT=/tmp/nanobase-hbys-public-e2e
LOG=$ROOT/run-2b-post-fix/watchdog.log
STATUS=$ROOT/run-2b-post-fix/watchdog-status.json
DI_JOB=5b08f6f1-9473-4d74-aa0a-50aef7725df1
DOC=025b2e69-d1bd-4d06-b28e-0ca35853a60e
STALL_MINUTES=20
mkdir -p "$ROOT/run-2b-post-fix"
last_progress=""
last_change_ts=$(date +%s)

log(){ echo "$(date -Is) $*" | tee -a "$LOG"; }

while true; do
  now=$(date +%s)
  backend=$(docker inspect -f '{{.State.Health.Status}}' specai-legal-backend-1 2>/dev/null || echo missing)
  di=$(docker inspect -f '{{.State.Health.Status}}' specai-legal-document-intelligence-1 2>/dev/null || echo missing)
  runner=$(pgrep -f 'public_hbys_blind_e2e_run2b.py' | head -1 || true)

  di_line=$(docker exec -i specai-legal-document-intelligence-1 python -u - <<PY
import sqlite3, json
c = sqlite3.connect("/var/lib/specai/jobs.sqlite3")
r = c.execute(
    "select status, progress, current_stage, substr(message,1,180), updated_at "
    "from processing_job where id=?",
    ("$DI_JOB",),
).fetchone()
print(json.dumps({"status": r[0], "progress": r[1], "stage": r[2], "message": r[3], "updated": r[4]} if r else {"missing": True}))
PY
)

  progress_key=$(PROGRESS_JSON="$di_line" python3 - <<'PY'
import json, os
d = json.loads(os.environ["PROGRESS_JSON"])
print(d.get("status"), d.get("progress"), (d.get("message") or "")[:100])
PY
)
  if [[ "$progress_key" != "$last_progress" ]]; then
    last_progress="$progress_key"
    last_change_ts=$now
  fi
  stall_min=$(( (now - last_change_ts) / 60 ))
  di_status=$(PROGRESS_JSON="$di_line" python3 - <<'PY'
import json, os
print(json.loads(os.environ["PROGRESS_JSON"]).get("status", ""))
PY
)

  if [[ -f $ROOT/run-2b-post-fix/run-summary.json ]] && ! pgrep -f 'public_hbys_blind_e2e_run2b.py' >/dev/null; then
    pipe=$(python3 - <<PY
import json
print(json.load(open("$ROOT/run-2b-post-fix/run-summary.json")).get("pipelineE2EStatus"))
PY
)
    log "RUN2B_FINISHED pipeline=$pipe"
    printf '%s\n' "{\"phase\":\"FINISHED\",\"pipeline\":\"$pipe\",\"backend\":\"$backend\"}" > "$STATUS"
    exit 0
  fi

  if [[ "$backend" != "healthy" ]]; then
    log "INTERVENE backend=$backend — recreate backend"
    cd /data/nanobaseai/legal
    sudo docker compose -p specai-legal -f compose.yaml -f compose.easymeeting.yaml --env-file /etc/nanobaseai/legal.env up -d backend >>"$LOG" 2>&1 || true
    sleep 30
  fi

  if [[ -z "${runner:-}" && ! -f $ROOT/run-2b-post-fix/run-summary.json ]]; then
    if [[ "$di_status" == "RUNNING" || "$di_status" == "COMPLETED" ]]; then
      log "ALERT runner dead while DI=$di_status job=$DI_JOB doc=$DOC"
      printf '%s\n' "{\"phase\":\"ALERT_RUNNER_DEAD\",\"di\":\"$di_status\",\"diJob\":\"$DI_JOB\",\"doc\":\"$DOC\"}" > "$STATUS"
    else
      log "INTERVENE runner dead — restart RUN_2B"
      export SPECAI_API=http://127.0.0.1:8098
      export HBYS_E2E_ROOT=$ROOT
      export HBYS_PDF=$ROOT/source/hbys-technical-specification.pdf
      export HBYS_EXPECTED_PAGES=235
      nohup python3 /data/nanobaseai/legal/scripts/public_hbys_blind_e2e_run2b.py >>"$ROOT/run-2b-post-fix/runner.stdout" 2>&1 &
      log "restarted run2b pid=$!"
    fi
  fi

  if [[ "$di_status" == "RUNNING" && "$stall_min" -ge "$STALL_MINUTES" ]]; then
    log "INTERVENE DI stall ${stall_min}m — restart document-intelligence (checkpoint resume)"
    cd /data/nanobaseai/legal
    sudo docker compose -p specai-legal -f compose.yaml -f compose.easymeeting.yaml --env-file /etc/nanobaseai/legal.env restart document-intelligence >>"$LOG" 2>&1 || true
    last_change_ts=$now
    sleep 60
  fi

  PROGRESS_JSON="$di_line" STALL="$stall_min" BACKEND="$backend" DIH="$di" RUNNER="${runner:-null}" python3 - <<'PY' > "$STATUS"
import json, os
payload = {
  "phase": "WATCHING",
  "backend": os.environ["BACKEND"],
  "diHealth": os.environ["DIH"],
  "runner": os.environ["RUNNER"],
  "stallMin": int(os.environ["STALL"]),
  "diJob": json.loads(os.environ["PROGRESS_JSON"]),
}
print(json.dumps(payload, indent=2))
PY
  log "ok backend=$backend di=$di runner=${runner:-none} stallMin=$stall_min progress=$progress_key"
  sleep 120
done
