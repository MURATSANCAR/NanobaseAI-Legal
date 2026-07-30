#!/usr/bin/env python3
"""Phase 5: stale-worker fencing + cancel/persist barriers via PAUSE_BEFORE_PERSIST."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
HOLD_PROJECT = "2175cb7c-5c01-41f1-a0e0-a4888ed1e4a3"
REQ = "184e7eac-7808-4b79-86df-a70bf619bc33"
API = "http://127.0.0.1:8098"
TOKEN = "phase5-fault-token"
REPORT = Path("/tmp/phase5_stale_cancel_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    path = f"/tmp/p5_{uuid.uuid4().hex}.sql"
    Path(path).write_text(sql)
    name = Path(path).name
    subprocess.check_call(["sudo", "docker", "cp", path, f"actenora-prodlike-postgres:/tmp/{name}"])
    return subprocess.check_output(
        [
            "sudo",
            "docker",
            "exec",
            "-e",
            f"PGPASSWORD={env('DATABASE_PASSWORD')}",
            "actenora-prodlike-postgres",
            "psql",
            "-U",
            env("DATABASE_USER"),
            "-d",
            "specai",
            "-v",
            "ON_ERROR_STOP=1",
            "-At",
            "-f",
            f"/tmp/{name}",
        ],
        text=True,
    ).strip()


def login() -> str:
    data = json.dumps(
        {
            "email": env("SPECAI_LOCAL_ADMIN_EMAIL"),
            "password": env("SPECAI_LOCAL_ADMIN_PASSWORD"),
        }
    ).encode()
    req = urllib.request.Request(
        API + "/api/v1/auth/login",
        data=data,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())["accessToken"]


def api(method: str, path: str, token: str, body=None, correlation_id: str | None = None, timeout=120):
    data = None if body is None else json.dumps(body).encode()
    cid = correlation_id or str(uuid.uuid4())
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-Organization-Id": ORG,
            "X-Correlation-ID": cid,
            "X-Fault-Injection-Token": TOKEN,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None), cid
    except urllib.error.HTTPError as exc:
        try:
            raw = exc.read().decode("utf-8", "replace")
        except Exception:
            raw = ""
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = {"raw": raw[:800]}
        return exc.code, payload, cid
    except Exception as exc:  # noqa: BLE001
        return 0, {"error": str(exc)}, cid


def set_pause_rule(token: str, correlation_id: str, action: str, timeout_ms: int = 300000) -> None:
    body = {
        "enabled": True,
        "rules": [
            {
                "match": {"correlationId": correlation_id},
                "action": {"type": action, "timeoutMs": timeout_ms},
                "maxExecutions": 1,
            }
        ],
    }
    st, payload, _ = api("POST", "/api/v1/internal/compliance-fault-injection/rules", token, body)
    if st != 200:
        raise RuntimeError(f"fault rules failed: {st} {payload}")


def release_pause(token: str, correlation_id: str, action: str) -> None:
    path = (
        f"/api/v1/internal/compliance-fault-injection/release"
        f"?matchKey=correlationId={correlation_id}&action={action}"
    )
    api("POST", path, token, body={})


def clear_rules(token: str) -> None:
    api("POST", "/api/v1/internal/compliance-fault-injection/rules", token, {"enabled": False, "rules": []})


def isolate_demo() -> None:
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement set project_id='{HOLD_PROJECT}'
 where organization_id='{ORG}' and project_id='{PROJECT}'
   and id <> '{REQ}';
"""
    )


def restore_demo() -> None:
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement set project_id='{PROJECT}'
 where organization_id='{ORG}' and project_id='{HOLD_PROJECT}';
"""
    )


def start_job(token: str, correlation_id: str) -> str:
    st, payload, _ = api(
        "POST",
        f"/api/v1/tenders/{PROJECT}/compliance-analyses",
        token,
        body={},
        correlation_id=correlation_id,
    )
    if isinstance(payload, dict) and payload.get("id"):
        return payload["id"]
    if isinstance(payload, dict) and payload.get("jobId"):
        return payload["jobId"]
    raise RuntimeError(f"start job failed {st} {payload}")


def cancel_job(token: str, job_id: str):
    return api("POST", f"/api/v1/compliance-analyses/{job_id}/cancel", token, body={})


def task_snap(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(jsonb_agg(jsonb_build_object(
  'taskId', id,
  'status', status,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'attemptCount', attempt_count,
  'errorCode', error_code
) order by created_at), '[]'::jsonb)::text
from requirement_matching_task where compliance_job_id='{job_id}';
"""
    ).splitlines()[-1]
    return {"tasks": json.loads(raw)}


def job_snap(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'errorCode', last_error_code
)::text
from compliance_analysis_job where id='{job_id}';
"""
    ).splitlines()[-1]
    return json.loads(raw)


def eval_count(job_id: str) -> int:
    return int(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from compliance_evaluation where analysis_job_id='{job_id}';
"""
        ).splitlines()[-1]
    )


def wait_pause_in_logs(correlation_id: str, action: str, timeout_s: int = 400) -> bool:
    deadline = time.time() + timeout_s
    needle = f"COMPLIANCE_FAULT_INJECTION_PAUSE action={action}"
    while time.time() < deadline:
        logs = subprocess.check_output(
            ["sudo", "docker", "logs", "--since", "10m", "specai-legal-backend-1"],
            text=True,
            stderr=subprocess.STDOUT,
        )
        if needle in logs and correlation_id in logs:
            return True
        # also wait for RUNNING task as proxy
        time.sleep(2)
    return False


def force_expire(job_id: str) -> None:
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update compliance_analysis_job
   set lease_expires_at = clock_timestamp() - interval '1 second'
 where id='{job_id}';
update requirement_matching_task
   set lease_expires_at = clock_timestamp() - interval '1 second'
 where compliance_job_id='{job_id}'
   and status in ('RUNNING','READY_FOR_MODEL','CLAIMED','WAITING_FOR_SLOT');
"""
    )


def wait_gen(job_id: str, min_gen: int, timeout_s: int = 120) -> dict:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        snap = task_snap(job_id)
        gens = [t.get("leaseGeneration") or 0 for t in snap["tasks"]]
        if gens and max(gens) >= min_gen:
            return snap
        time.sleep(2)
    return task_snap(job_id)


def scenario_cancel_before_persist(token: str) -> dict:
    cid = str(uuid.uuid4())
    set_pause_rule(token, cid, "PAUSE_BEFORE_PERSIST")
    job_id = start_job(token, cid)
    paused = wait_pause_in_logs(cid, "PAUSE_BEFORE_PERSIST", 420)
    before_eval = eval_count(job_id)
    t0 = time.time()
    st, payload, _ = cancel_job(token, job_id)
    cancel_ms = int((time.time() - t0) * 1000)
    release_pause(token, cid, "PAUSE_BEFORE_PERSIST")
    time.sleep(5)
    job = job_snap(job_id)
    tasks = task_snap(job_id)
    after_eval = eval_count(job_id)
    passed = (
        paused
        and job.get("status") == "CANCELLED"
        and after_eval == before_eval
    )
    return {
        "test": "cancel_persist_A",
        "jobId": job_id,
        "correlationId": cid,
        "paused": paused,
        "cancelHttp": st,
        "cancelLatencyMs": cancel_ms,
        "job": job,
        "tasks": tasks,
        "evalBefore": before_eval,
        "evalAfter": after_eval,
        "result": "PASS" if passed else "FAIL",
    }


def scenario_persist_before_cancel(token: str) -> dict:
    cid = str(uuid.uuid4())
    # no pause — let job complete
    clear_rules(token)
    job_id = start_job(token, cid)
    deadline = time.time() + 420
    job = {}
    while time.time() < deadline:
        job = job_snap(job_id)
        if job.get("status") in {"COMPLETED", "FAILED", "CANCELLED"}:
            break
        time.sleep(3)
    st, payload, _ = cancel_job(token, job_id)
    job2 = job_snap(job_id)
    evals = eval_count(job_id)
    passed = (
        job.get("status") == "COMPLETED"
        and job2.get("status") == "COMPLETED"
        and evals >= 1
        and st in {200, 409, 422}
    )
    return {
        "test": "cancel_persist_B",
        "jobId": job_id,
        "correlationId": cid,
        "statusBeforeCancel": job.get("status"),
        "statusAfterCancel": job2.get("status"),
        "cancelHttp": st,
        "cancelBody": payload,
        "evaluations": evals,
        "result": "PASS" if passed else "FAIL",
    }


def scenario_stale_worker(token: str) -> dict:
    cid = str(uuid.uuid4())
    set_pause_rule(token, cid, "PAUSE_BEFORE_PERSIST")
    job_id = start_job(token, cid)
    paused = wait_pause_in_logs(cid, "PAUSE_BEFORE_PERSIST", 420)
    before = task_snap(job_id)
    gen_before = max((t.get("leaseGeneration") or 0) for t in before["tasks"] or [0])
    worker_a = (before["tasks"][0].get("claimedBy") if before["tasks"] else None)
    force_expire(job_id)
    after_reclaim = wait_gen(job_id, gen_before + 1, 150)
    # allow worker B path (concurrency>=2) to proceed without the old pause rule matching new cid
    # Keep pause on original cid; Worker B uses same correlation from republished event!
    # So Worker B may also hit the same PAUSE rule if maxExecutions=1 — good, only A paused once.
    deadline = time.time() + 420
    terminal = None
    while time.time() < deadline:
        job = job_snap(job_id)
        if job.get("status") in {"COMPLETED", "FAILED", "CANCELLED"}:
            terminal = job
            break
        tasks = task_snap(job_id)
        # if a newer generation is RUNNING without pause, wait
        time.sleep(3)
        _ = tasks
    # release A after B likely done or timeout
    release_pause(token, cid, "PAUSE_BEFORE_PERSIST")
    time.sleep(8)
    if terminal is None:
        terminal = job_snap(job_id)
    tasks_final = task_snap(job_id)
    gen_after = max((t.get("leaseGeneration") or 0) for t in tasks_final["tasks"] or [0])
    evals = eval_count(job_id)
    logs = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "30m", "specai-legal-backend-1"],
        text=True,
        stderr=subprocess.STDOUT,
    )
    stale_seen = "STALE_WORKER_RESULT" in logs or "PERSIST_REJECTED_STALE" in logs
    passed = (
        paused
        and gen_after > gen_before
        and terminal.get("status") in {"COMPLETED", "FAILED"}
        and stale_seen
        and evals <= 1
    )
    return {
        "test": "stale_worker",
        "jobId": job_id,
        "correlationId": cid,
        "paused": paused,
        "workerA": worker_a,
        "taskGenerationBefore": gen_before,
        "taskGenerationAfter": gen_after,
        "jobFinal": terminal,
        "tasksFinal": tasks_final,
        "evaluations": evals,
        "staleSeenInLogs": stale_seen,
        "result": "PASS" if passed else "FAIL",
    }


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    token = login()
    # verify FI enabled
    st, snap, _ = api("GET", "/api/v1/internal/compliance-fault-injection", token)
    if st != 200 or not (isinstance(snap, dict) and snap.get("enabled")):
        print(json.dumps({"error": "fault_injection_disabled", "status": st, "snap": snap}))
        return 2
    isolate_demo()
    report = {"startedAt": time.time(), "faultSnapshot": snap, "cases": {}}
    try:
        if mode in {"all", "cancelA"}:
            report["cases"]["cancelA"] = scenario_cancel_before_persist(token)
            clear_rules(token)
        if mode in {"all", "cancelB"}:
            report["cases"]["cancelB"] = scenario_persist_before_cancel(token)
            clear_rules(token)
        if mode in {"all", "stale"}:
            report["cases"]["stale"] = scenario_stale_worker(token)
            clear_rules(token)
    finally:
        clear_rules(token)
        restore_demo()
    results = [c.get("result") for c in report["cases"].values()]
    report["result"] = "PASS" if results and all(r == "PASS" for r in results) else "FAIL"
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print(json.dumps(report, indent=2, default=str))
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
