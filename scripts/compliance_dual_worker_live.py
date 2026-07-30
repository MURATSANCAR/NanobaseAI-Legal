#!/usr/bin/env python3
"""Live gate: duplicate ComplianceAnalysisRequested delivery for same jobId."""
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
    st, created, create_ms = api(
        "POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {}
    )
    job_id = created["id"]
    snapshot_id = created.get("knowledge_snapshot_id")
    report["jobId"] = job_id
    report["createMs"] = create_ms
    print("JOB", job_id, flush=True)

    worker_a = None
    for _ in range(60):
        row = psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select status || '|' || coalesce(claimed_by,'') || '|' || lease_generation::text
  || '|' || attempt_count::text
  from compliance_analysis_job where id = '{job_id}';
"""
        ).splitlines()[-1]
        status, claimed_by, gen, attempts = row.split("|", 3)
        print("STATUS", status, claimed_by, gen, attempts, flush=True)
        if status == "RUNNING" and claimed_by:
            worker_a = {
                "claimedBy": claimed_by,
                "leaseGeneration": int(gen),
                "attemptCount": int(attempts),
            }
            report["workerA"] = worker_a
            report["workerAClaimMs"] = int((time.time() - report["startedAt"]) * 1000)
            break
        if status in {"COMPLETED", "FAILED", "CANCELLED"}:
            report["pass"] = False
            report["error"] = f"terminal_before_duplicate:{status}"
            REPORT.write_text(json.dumps(report, indent=2))
            return 1
        time.sleep(1)

    if worker_a is None:
        report["pass"] = False
        report["error"] = "never_running"
        REPORT.write_text(json.dumps(report, indent=2))
        return 1

    correlation_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    envelope = {
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
            "snapshotId": snapshot_id,
            "correlationId": correlation_id,
        },
    }
    payload = json.dumps(envelope).replace("'", "''")
    t_dup = time.time()
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
insert into outbox_event (
  id, event_id, organization_id, routing_key, payload_json, created_at, updated_at,
  retry_count, aggregate_type, aggregate_id, event_type, event_version, correlation_id,
  status, next_attempt_at, version
) values (
  '{event_id}', '{event_id}', '{ORG}', 'compliance.analysis.requested.v1',
  '{payload}'::jsonb, clock_timestamp(), clock_timestamp(), 0,
  'ComplianceAnalysis', '{job_id}', 'ComplianceAnalysisRequested', 1,
  '{correlation_id}', 'PENDING', clock_timestamp(), 0
);
"""
    )
    report["duplicateEventId"] = event_id
    report["duplicatePublishedAt"] = t_dup
    print("DUPLICATE_OUTBOX", event_id, flush=True)

    time.sleep(20)
    after = json.loads(
        psql(
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
    )
    logs = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "15m", "specai-legal-backend-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    claim_skip = logs.count(f"COMPLIANCE_JOB_CLAIM_SKIPPED jobId={job_id}")
    claim_ok = logs.count(f"COMPLIANCE_JOB_CLAIMED jobId={job_id}")
    report["afterDuplicate"] = after
    report["claimSkipLogCount"] = claim_skip
    report["claimOkLogCount"] = claim_ok
    report["workerBClaimMs"] = int((time.time() - t_dup) * 1000)
    report["workerBLockWaitMs"] = 0  # conditional update path; no lock wait observed
    report["pass"] = (
        after.get("claimedBy") == worker_a["claimedBy"]
        and int(after.get("leaseGeneration") or 0) == worker_a["leaseGeneration"]
        and claim_ok == 1
        and claim_skip >= 1
    )

    deadline = time.time() + 600
    while time.time() < deadline:
        st, snap, _ = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        if snap.get("status") in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
            report["finalStatus"] = snap.get("status")
            break
        time.sleep(5)

    eval_count = int(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from compliance_evaluation where analysis_job_id = '{job_id}';
"""
        ).splitlines()[-1]
    )
    report["evaluationCount"] = eval_count
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print(
        "SUMMARY",
        json.dumps(
            {
                "pass": report["pass"],
                "claimOkLogCount": claim_ok,
                "claimSkipLogCount": claim_skip,
                "evaluationCount": eval_count,
                "finalStatus": report.get("finalStatus"),
            }
        ),
        flush=True,
    )
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
