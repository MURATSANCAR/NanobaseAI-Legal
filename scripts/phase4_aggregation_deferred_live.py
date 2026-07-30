#!/usr/bin/env python3
"""Phase 4: AGGREGATION_DEFERRED while task paused after prepare (READY_FOR_MODEL)."""
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
REPORT = Path("/tmp/phase4_aggregation_deferred_report.json")


def env(k: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{k}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def login() -> str:
    data = json.dumps(
        {"email": env("SPECAI_LOCAL_ADMIN_EMAIL"), "password": env("SPECAI_LOCAL_ADMIN_PASSWORD")}
    ).encode()
    req = urllib.request.Request(
        API + "/api/v1/auth/login", data=data, method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())["accessToken"]


def api(method, path, token, body=None, correlation_id=None, fault_token=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
        "Content-Type": "application/json",
        "X-Organization-Id": ORG,
        "X-Correlation-ID": correlation_id or str(uuid.uuid4()),
    }
    if fault_token:
        headers["X-Fault-Injection-Token"] = fault_token
    req = urllib.request.Request(API + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None)


def psql(sql: str) -> str:
    Path("/tmp/p4_agg.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/p4_agg.sql", "actenora-prodlike-postgres:/tmp/p4_agg.sql"]
    )
    return subprocess.check_output(
        [
            "sudo", "docker", "exec", "-e", f"PGPASSWORD={env('DATABASE_PASSWORD')}",
            "actenora-prodlike-postgres", "psql", "-U", env("DATABASE_USER"), "-d", "specai",
            "-At", "-f", "/tmp/p4_agg.sql",
        ],
        text=True,
    ).strip()


def main() -> int:
    report = {"test": "aggregation_deferred_live", "startedAt": time.time()}
    token = login()
    fault = env("COMPLIANCE_FAULT_INJECTION_TOKEN")
    correlation_id = str(uuid.uuid4())
    api(
        "POST",
        "/api/v1/internal/compliance-fault-injection/rules",
        token,
        {
            "enabled": True,
            "rules": [
                {
                    "match": {"correlationId": correlation_id},
                    "action": {"type": "PAUSE_AFTER_PREPARE", "timeoutMs": 180000},
                    "maxExecutions": 1,
                }
            ],
        },
        fault_token=fault,
    )
    st, created = api(
        "POST",
        f"/api/v1/tenders/{PROJECT}/compliance-analyses",
        token,
        {},
        correlation_id=correlation_id,
    )
    job_id = created["id"]
    report["jobId"] = job_id
    report["correlationId"] = correlation_id
    print("JOB", job_id, flush=True)
    ready = False
    for _ in range(90):
        row = psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select status from requirement_matching_task where compliance_job_id='{job_id}' limit 1;
"""
        ).splitlines()[-1]
        print("TASK", row, flush=True)
        if row == "READY_FOR_MODEL":
            ready = True
            break
        if row in {"COMPLETED", "FAILED", "CANCELLED"}:
            break
        time.sleep(2)
    if not ready:
        report["pass"] = False
        report["error"] = "never_ready_for_model"
        REPORT.write_text(json.dumps(report, indent=2))
        return 1
    st, fin = api(
        "POST",
        f"/api/v1/internal/compliance-jobs/{ORG}/{job_id}/finalize",
        token,
        {},
        fault_token=fault,
    )
    report["finalizeWhileActive"] = fin
    job_status = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select status from compliance_analysis_job where id='{job_id}';
"""
    ).splitlines()[-1]
    report["jobStatusWhileDeferred"] = job_status
    api(
        "POST",
        f"/api/v1/internal/compliance-fault-injection/release?matchKey=correlationId={correlation_id}&action=PAUSE_AFTER_PREPARE",
        token,
        {},
        fault_token=fault,
    )
    deadline = time.time() + 600
    final = None
    while time.time() < deadline:
        st, snap = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        if snap.get("status") in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
            final = snap
            break
        time.sleep(3)
    report["final"] = final
    report["pass"] = (
        fin.get("status") == "AGGREGATION_DEFERRED"
        and job_status == "RUNNING"
        and final is not None
        and final.get("status") in {"COMPLETED", "PARTIALLY_COMPLETED"}
    )
    api(
        "POST",
        "/api/v1/internal/compliance-fault-injection/rules",
        token,
        {"enabled": False, "rules": []},
        fault_token=fault,
    )
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print(
        "SUMMARY",
        json.dumps(
            {
                "pass": report["pass"],
                "finalize": fin,
                "jobWhile": job_status,
                "final": (final or {}).get("status"),
            }
        ),
        flush=True,
    )
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
