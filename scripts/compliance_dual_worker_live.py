#!/usr/bin/env python3
"""Live gate: duplicate delivery of same ComplianceAnalysisRequested event ID."""
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
REPORT = Path("/tmp/compliance_dual_worker_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/dual_worker.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/dual_worker.sql", "actenora-prodlike-postgres:/tmp/dual_worker.sql"]
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
            "/tmp/dual_worker.sql",
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
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None), int((time.time() - t0) * 1000)


def main() -> int:
    report: dict = {"test": "dual_worker_duplicate_delivery", "startedAt": time.time()}
    token = login()
    st, created, _ = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
    job_id = created["id"]
    report["jobId"] = job_id
    print("JOB", job_id, flush=True)

    # Wait until RUNNING (first claim)
    claimed_by = None
    for _ in range(60):
        row = psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select status || '|' || coalesce(claimed_by,'') || '|' || lease_generation::text
  from compliance_analysis_job where id = '{job_id}';
"""
        ).splitlines()[-1]
        status, claimed_by, gen = row.split("|", 2)
        print("STATUS", status, "claimedBy=", claimed_by, "gen=", gen, flush=True)
        if status == "RUNNING":
            report["workerA"] = {"claimedBy": claimed_by, "leaseGeneration": int(gen)}
            report["workerAClaimMs"] = int((time.time() - report["startedAt"]) * 1000)
            break
        if status in {"COMPLETED", "FAILED", "CANCELLED"}:
            report["pass"] = False
            report["error"] = f"terminal_before_duplicate:{status}"
            REPORT.write_text(json.dumps(report, indent=2))
            return 1
        time.sleep(1)

    # Inject duplicate requested event with a NEW outbox/event id but SAME jobId
    # (simulates redelivery of same business event; claim must no-op)
    correlation_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    payload = json.dumps(
        {
            "eventId": event_id,
            "eventType": "ComplianceAnalysisRequested",
            "eventVersion": 1,
            "organizationId": ORG,
            "correlationId": correlation_id,
            "causationId": correlation_id,
            "occurredAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "payload": {
                "jobId": job_id,
                "projectId": PROJECT,
                "snapshotId": None,
                "correlationId": correlation_id,
            },
        }
    )
    # Prefer publishing via outbox so consumer path is real
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
insert into outbox_event (
  id, aggregate_type, aggregate_id, event_type, event_version, routing_key,
  payload_json, organization_id, correlation_id, created_at, published_at
) values (
  '{event_id}', 'ComplianceAnalysis', '{job_id}', 'ComplianceAnalysisRequested', 1,
  'compliance.analysis.requested.v1',
  '{payload.replace("'", "''")}'::jsonb,
  '{ORG}', '{correlation_id}', clock_timestamp(), null
);
"""
    )
    report["duplicateEventId"] = event_id
    report["duplicatePublishedAt"] = time.time()
    print("DUPLICATE_OUTBOX", event_id, flush=True)

    # Observe claim skip logs / generation unchanged / single evaluation
    time.sleep(15)
    after = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'attemptCount', attempt_count
)::text
from compliance_analysis_job where id = '{job_id}';
"""
    ).splitlines()[-1]
    after_job = json.loads(after)
    eval_count = int(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from compliance_evaluation where analysis_job_id = '{job_id}';
"""
        ).splitlines()[-1]
    )
    skip_logs = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "10m", "specai-legal-backend-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    claim_skip = skip_logs.count(f"COMPLIANCE_JOB_CLAIM_SKIPPED jobId={job_id}")
    claim_ok = skip_logs.count(f"COMPLIANCE_JOB_CLAIMED jobId={job_id}")
    report["after"] = after_job
    report["evaluationCount"] = eval_count
    report["claimSkipLogCount"] = claim_skip
    report["claimOkLogCount"] = claim_ok
    report["pass"] = (
        after_job.get("claimedBy") == report["workerA"]["claimedBy"]
        and int(after_job.get("leaseGeneration") or 0) == report["workerA"]["leaseGeneration"]
        and claim_ok == 1
        and claim_skip >= 1
    )
    # Wait for job terminal to avoid leaving orphan RUNNING jobs
    deadline = time.time() + 600
    while time.time() < deadline:
        st, snap, _ = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        if snap.get("status") in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
            report["finalStatus"] = snap.get("status")
            break
        time.sleep(5)
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print("SUMMARY", json.dumps({k: report[k] for k in ("pass", "claimOkLogCount", "claimSkipLogCount", "evaluationCount", "finalStatus") if k in report}), flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
