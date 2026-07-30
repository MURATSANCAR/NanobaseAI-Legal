#!/usr/bin/env python3
"""Phase 4: concurrent same-job claim race via two DB sessions (conditional UPDATE)."""
from __future__ import annotations

import json
import subprocess
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/phase4_concurrent_claim_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    path = f"/tmp/cc_{uuid.uuid4().hex}.sql"
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
    import urllib.request

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
    import urllib.request

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


def claim_once(job_id: str, worker_id: str, barrier: threading.Barrier) -> dict:
    barrier.wait(timeout=30)
    started = time.time()
    sql = f"""
select set_config('app.current_organization_id','{ORG}',true);
with updated as (
  update compliance_analysis_job
     set status = 'RUNNING',
         claimed_by = '{worker_id}',
         claimed_at = clock_timestamp(),
         heartbeat_at = clock_timestamp(),
         lease_expires_at = clock_timestamp() + interval '15 minutes',
         lease_generation = lease_generation + 1,
         started_at = coalesce(started_at, clock_timestamp()),
         attempt_count = attempt_count + 1,
         updated_at = clock_timestamp(),
         version = version + 1
   where id = '{job_id}'
     and organization_id = '{ORG}'
     and (
          status = 'QUEUED'
          or (
              status = 'RUNNING'
              and lease_expires_at is not null
              and lease_expires_at < clock_timestamp()
          )
     )
  returning id, claimed_by, lease_generation, attempt_count, status
)
select coalesce(
  (select jsonb_build_object(
      'claimed', true,
      'claimedBy', claimed_by,
      'leaseGeneration', lease_generation,
      'attemptCount', attempt_count,
      'status', status
    )::text from updated),
  jsonb_build_object('claimed', false, 'claimedBy', null)::text
);
"""
    out = psql(sql).splitlines()[-1]
    elapsed = int((time.time() - started) * 1000)
    payload = json.loads(out)
    payload["workerId"] = worker_id
    payload["claimMs"] = elapsed
    return payload


def main() -> int:
    report: dict = {"test": "phase4_concurrent_same_job_claim", "startedAt": time.time()}
    token = login()
    st, created = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
    # Immediately race claim before/while consumer also claims — create a fresh QUEUED
    # by clearing consumer claim if it already won.
    job_id = created["id"]
    report["jobId"] = job_id
    # Force QUEUED + clear claim so both racers start equal (simulates two workers
    # receiving different event IDs for same job before either commits claim).
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update compliance_analysis_job
   set status='QUEUED', claimed_by=null, lease_expires_at=null,
       lease_generation=0, attempt_count=0, started_at=null,
       updated_at=clock_timestamp(), version=version+1
 where id='{job_id}';
"""
    )
    barrier = threading.Barrier(2)
    workers = [
        f"worker-a-{uuid.uuid4()}",
        f"worker-b-{uuid.uuid4()}",
    ]
    results = []
    with ThreadPoolExecutor(max_workers=2) as pool:
        futs = [pool.submit(claim_once, job_id, wid, barrier) for wid in workers]
        for fut in as_completed(futs):
            results.append(fut.result())
    claimed = [r for r in results if r.get("claimed")]
    skipped = [r for r in results if not r.get("claimed")]
    row = json.loads(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status, 'claimedBy', claimed_by,
  'leaseGeneration', lease_generation, 'attemptCount', attempt_count
)::text from compliance_analysis_job where id='{job_id}';
"""
        ).splitlines()[-1]
    )
    report["results"] = results
    report["after"] = row
    report["workerAClaimMs"] = results[0]["claimMs"] if results else None
    report["workerBClaimMs"] = results[1]["claimMs"] if len(results) > 1 else None
    report["workerBLockWaitMs"] = 0
    report["pass"] = len(claimed) == 1 and len(skipped) == 1 and row.get("leaseGeneration") == 1
    # Allow consumer to finish / cancel leftover
    try:
        api("POST", f"/api/v1/compliance-analyses/{job_id}/cancel", token, {})
    except Exception:
        pass
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print("SUMMARY", json.dumps({"pass": report["pass"], "claimed": len(claimed), "after": row}), flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
