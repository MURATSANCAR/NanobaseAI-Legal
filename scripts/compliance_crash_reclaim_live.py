#!/usr/bin/env python3
"""Live gate helper: force-expire a RUNNING job lease and wait for reclaim scheduler."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/compliance_crash_reclaim_report.json")


def env(key: str) -> str:
    out = subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    )
    return out.strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/crash_reclaim.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/crash_reclaim.sql", "actenora-prodlike-postgres:/tmp/crash_reclaim.sql"]
    )
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
            "/tmp/crash_reclaim.sql",
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


def api(method: str, path: str, token: str, body=None):
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
            "X-Correlation-ID": str(uuid.uuid4()),
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None)


def job_row(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'leaseExpiresAt', lease_expires_at,
  'attemptCount', attempt_count,
  'heartbeatAt', heartbeat_at
)::text
from compliance_analysis_job where id = '{job_id}';
"""
    ).splitlines()[-1]
    return json.loads(raw)


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: compliance_crash_reclaim_live.py <jobId> [--kill-backend]", flush=True)
        return 2
    job_id = sys.argv[1]
    kill_backend = "--kill-backend" in sys.argv
    report: dict = {
        "test": "crash_reclaim_live",
        "jobId": job_id,
        "startedAt": time.time(),
        "killBackend": kill_backend,
    }
    before = job_row(job_id)
    report["before"] = before
    print("BEFORE", json.dumps(before), flush=True)
    if before.get("status") != "RUNNING":
        report["pass"] = False
        report["error"] = "job_not_running"
        REPORT.write_text(json.dumps(report, indent=2, default=str))
        return 1

    if kill_backend:
        # SIGKILL-equivalent: docker kill (not graceful stop)
        subprocess.check_call(["sudo", "docker", "kill", "specai-legal-backend-1"])
        report["workerAKilledAt"] = time.time()
        print("WORKER_A docker kill issued", flush=True)

    # Force lease expiry so reclaim does not wait DEFAULT_LEASE (15m)
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update compliance_analysis_job
   set lease_expires_at = clock_timestamp() - interval '1 second',
       updated_at = clock_timestamp()
 where id = '{job_id}';
update requirement_matching_task
   set lease_expires_at = clock_timestamp() - interval '1 second',
       updated_at = clock_timestamp()
 where compliance_job_id = '{job_id}'
   and status in ('RUNNING', 'READY_FOR_MODEL');
"""
    )
    report["leaseForcedExpiredAt"] = time.time()
    print("LEASE_FORCED_EXPIRED", flush=True)

    if kill_backend:
        # Restart backend so reclaim scheduler + consumer are alive as Worker B
        subprocess.check_call(
            [
                "bash",
                "-lc",
                "cd /data/nanobaseai/legal && sudo docker compose -f compose.yaml "
                "-f compose.easymeeting.yaml --env-file /etc/nanobaseai/legal.env "
                "up -d backend",
            ]
        )
        report["workerBStartedAt"] = time.time()
        print("WORKER_B backend restarted", flush=True)
        # Wait for healthy
        for _ in range(60):
            try:
                urllib.request.urlopen(API + "/actuator/health", timeout=5)
                break
            except Exception:
                time.sleep(2)

    # Wait for reclaim scheduler (30s interval) + re-claim
    deadline = time.time() + 180
    transitions = []
    while time.time() < deadline:
        snap = job_row(job_id)
        if not transitions or transitions[-1].get("status") != snap.get("status") or transitions[-1].get(
            "leaseGeneration"
        ) != snap.get("leaseGeneration"):
            transitions.append({"at": time.time(), **snap})
            print("TRANSITION", json.dumps(snap), flush=True)
        if (
            snap.get("status") in {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}
            or (
                snap.get("status") == "RUNNING"
                and int(snap.get("leaseGeneration") or 0) > int(before.get("leaseGeneration") or 0)
            )
            or snap.get("status") == "QUEUED"
        ):
            # keep waiting for terminal if reclaim happened
            if snap.get("status") in {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}:
                break
        time.sleep(3)

    final = job_row(job_id)
    report["transitions"] = transitions
    report["final"] = final
    report["finishedAt"] = time.time()
    gen_increased = int(final.get("leaseGeneration") or 0) > int(before.get("leaseGeneration") or 0)
    attempt_increased = int(final.get("attemptCount") or 0) > int(before.get("attemptCount") or 0)
    report["pass"] = (
        gen_increased
        and attempt_increased
        and final.get("status") in {"COMPLETED", "PARTIALLY_COMPLETED", "RUNNING", "QUEUED"}
    )
    # Stronger pass if terminal
    if final.get("status") in {"COMPLETED", "PARTIALLY_COMPLETED"}:
        report["pass"] = gen_increased and attempt_increased
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print("SUMMARY", json.dumps({"pass": report["pass"], "final": final}), flush=True)
    print("REPORT", REPORT, flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
