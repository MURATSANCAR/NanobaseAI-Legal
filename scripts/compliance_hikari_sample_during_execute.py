#!/usr/bin/env python3
"""Sample PG activity + API latency while a compliance job is RUNNING (execute phase)."""
from __future__ import annotations

import json
import subprocess
import time
import urllib.request
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/compliance_hikari_sample_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/hikari_sample.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/hikari_sample.sql", "actenora-prodlike-postgres:/tmp/hikari_sample.sql"]
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
            "-At",
            "-f",
            "/tmp/hikari_sample.sql",
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
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None), int((time.time() - t0) * 1000)


def pg_activity() -> list[dict]:
    raw = psql(
        """
select coalesce(jsonb_agg(jsonb_build_object(
  'state', state,
  'wait_event_type', wait_event_type,
  'wait_event', wait_event,
  'count', cnt
)), '[]'::jsonb)::text
from (
  select state, wait_event_type, wait_event, count(*)::int as cnt
    from pg_stat_activity
   where datname = 'specai'
   group by 1,2,3
) s;
"""
    ).splitlines()[-1]
    return json.loads(raw)


def idle_in_tx() -> int:
    return int(
        psql(
            """
select count(*) from pg_stat_activity
 where datname='specai' and state='idle in transaction';
"""
        ).splitlines()[-1]
    )


def main() -> int:
    report: dict = {"test": "hikari_pool_sample_during_execute", "startedAt": time.time()}
    token = login()
    st, created, _ = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
    job_id = created["id"]
    report["jobId"] = job_id
    print("JOB", job_id, flush=True)
    samples = []
    final = None
    for _ in range(120):
        st, snap, lat = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        status = snap.get("status")
        sample = {
            "at": time.time(),
            "status": status,
            "getLatencyMs": lat,
            "idleInTransaction": idle_in_tx(),
            "pgActivity": pg_activity(),
        }
        samples.append(sample)
        print(
            f"SAMPLE status={status} getLatencyMs={lat} idleInTx={sample['idleInTransaction']}",
            flush=True,
        )
        if status == "RUNNING" and len([s for s in samples if s["status"] == "RUNNING"]) >= 3:
            # one cancel latency probe under load
            # do NOT cancel — this sample is observational for a normal job
            pass
        if status in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
            final = snap
            break
        time.sleep(2)
    report["samples"] = samples
    report["final"] = final
    running_samples = [s for s in samples if s["status"] == "RUNNING"]
    report["pass"] = (
        final is not None
        and final.get("status") == "COMPLETED"
        and all(s["idleInTransaction"] == 0 for s in running_samples)
        and all(s["getLatencyMs"] < 2000 for s in samples)
        and len(running_samples) >= 1
    )
    report["note"] = (
        "Observational single-job sample (not full pool-size=5 multi-job pressure). "
        "Hikari actuator/prometheus was not reachable on host; PG idle-in-transaction used."
    )
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print("SUMMARY", json.dumps({"pass": report["pass"], "jobId": job_id, "runningSamples": len(running_samples)}), flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
