#!/usr/bin/env python3
"""Phase 5: docker-kill crash reclaim live gate."""
from __future__ import annotations

import json
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
HOLD = "2175cb7c-5c01-41f1-a0e0-a4888ed1e4a3"
REQ = "184e7eac-7808-4b79-86df-a70bf619bc33"
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/phase5_crash_reclaim_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    path = f"/tmp/p5cr_{uuid.uuid4().hex}.sql"
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


def api(method: str, path: str, token: str, body=None, cid: str | None = None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-Organization-Id": ORG,
            "X-Correlation-ID": cid or str(uuid.uuid4()),
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def job_row(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'attemptCount', attempt_count
)::text
from compliance_analysis_job where id='{job_id}';
"""
    ).splitlines()[-1]
    return json.loads(raw)


def task_row(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(jsonb_agg(jsonb_build_object(
  'taskId', id, 'status', status, 'claimedBy', claimed_by,
  'leaseGeneration', lease_generation, 'attemptCount', attempt_count
)), '[]'::jsonb)::text
from requirement_matching_task where compliance_job_id='{job_id}';
"""
    ).splitlines()[-1]
    return {"tasks": json.loads(raw)}


def main() -> int:
    token = login()
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement set project_id='{HOLD}'
 where organization_id='{ORG}' and project_id='{PROJECT}' and id <> '{REQ}';
"""
    )
    try:
        created = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
        job_id = created.get("id") or created.get("jobId")
        before = None
        for _ in range(90):
            before = job_row(job_id)
            if before.get("status") == "RUNNING":
                break
            time.sleep(2)
        if before is None or before.get("status") != "RUNNING":
            REPORT.write_text(json.dumps({"result": "FAIL", "error": "not_running", "before": before}, indent=2))
            return 1
        tasks_before = task_row(job_id)
        gen_before = max((t.get("leaseGeneration") or 0) for t in tasks_before["tasks"] or [0])
        worker_a = before.get("claimedBy")
        killed_at = time.time()
        subprocess.check_call(["sudo", "docker", "kill", "specai-legal-backend-1"])
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
update compliance_analysis_job
   set lease_expires_at = clock_timestamp() - interval '1 second'
 where id='{job_id}';
update requirement_matching_task
   set lease_expires_at = clock_timestamp() - interval '1 second'
 where compliance_job_id='{job_id}';
"""
        )
        subprocess.check_call(
            [
                "bash",
                "-lc",
                "cd /data/nanobaseai/legal && sudo COMPLIANCE_FAULT_INJECTION_ENABLED=false "
                "COMPLIANCE_FAULT_INJECTION_TOKEN= "
                "SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY=1 "
                "SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY=1 "
                "docker compose -f compose.yaml -f compose.easymeeting.yaml "
                "-f compose.orchestrator-ha.yaml --env-file /etc/nanobaseai/legal.env "
                "up -d --no-deps backend",
            ]
        )
        for _ in range(90):
            try:
                urllib.request.urlopen(API + "/actuator/health/readiness", timeout=5)
                break
            except Exception:
                time.sleep(2)
        terminal = None
        deadline = time.time() + 420
        while time.time() < deadline:
            snap = job_row(job_id)
            if snap.get("status") in {"COMPLETED", "FAILED", "CANCELLED"}:
                terminal = snap
                break
            time.sleep(3)
        if terminal is None:
            terminal = job_row(job_id)
        tasks_after = task_row(job_id)
        gen_after = max((t.get("leaseGeneration") or 0) for t in tasks_after["tasks"] or [0])
        worker_b = (tasks_after["tasks"][0].get("claimedBy") if tasks_after["tasks"] else None)
        evals = int(
            psql(
                f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from compliance_evaluation where analysis_job_id='{job_id}';
"""
            ).splitlines()[-1]
        )
        passed = (
            terminal.get("status") == "COMPLETED"
            and gen_after > gen_before
            and evals == 1
            and worker_b
            and worker_b != worker_a
        )
        report = {
            "jobId": job_id,
            "workerAId": worker_a,
            "workerBId": worker_b,
            "taskGenerationBefore": gen_before,
            "taskGenerationAfter": gen_after,
            "workerKilledAt": killed_at,
            "before": before,
            "after": terminal,
            "tasksAfter": tasks_after,
            "evaluations": evals,
            "result": "PASS" if passed else "FAIL",
        }
        REPORT.write_text(json.dumps(report, indent=2, default=str))
        print(json.dumps(report, indent=2, default=str))
        return 0 if passed else 1
    finally:
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement set project_id='{PROJECT}'
 where organization_id='{ORG}' and project_id='{HOLD}';
"""
        )


if __name__ == "__main__":
    raise SystemExit(main())
