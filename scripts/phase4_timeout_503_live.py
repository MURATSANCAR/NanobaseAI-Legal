#!/usr/bin/env python3
"""Phase 4: controlled timeout / 503 via AI orchestrator fault injection."""
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
ORCH = "http://127.0.0.1:8090"  # may be internal only — use docker exec curl fallback
REPORT = Path("/tmp/phase4_timeout_503_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def token_fault() -> str:
    try:
        return env("COMPLIANCE_FAULT_INJECTION_TOKEN")
    except subprocess.CalledProcessError:
        return env("AI_ORCHESTRATOR_FAULT_INJECTION_TOKEN")


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


def api(method: str, path: str, token: str, body=None, correlation_id: str | None = None):
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
        },
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None), cid


def orch_set_rules(rules: dict) -> None:
    token = token_fault()
    payload = json.dumps(rules)
    # Orchestrator is not published on host; talk via docker network.
    subprocess.check_call(
        [
            "sudo",
            "docker",
            "exec",
            "specai-legal-ai-orchestrator-1",
            "python",
            "-c",
            (
                "import json,urllib.request;"
                f"req=urllib.request.Request('http://127.0.0.1:8090/v1/test/fault-injection/rules',"
                f"data={payload!r}.encode(),method='POST',"
                f"headers={{'Content-Type':'application/json','X-Fault-Injection-Token':{token!r}}});"
                "print(urllib.request.urlopen(req,timeout=30).read().decode())"
            ),
        ]
    )


def psql(sql: str) -> str:
    Path("/tmp/p4_to.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/p4_to.sql", "actenora-prodlike-postgres:/tmp/p4_to.sql"]
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
            "/tmp/p4_to.sql",
        ],
        text=True,
    ).strip()


def wait_terminal(token: str, job_id: str, timeout_s: int = 600) -> dict:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        st, snap, _ = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        if snap.get("status") in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
            return snap
        time.sleep(2)
    raise TimeoutError(job_id)


def task_row(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'taskId', id, 'status', status, 'errorCode', error_code,
  'attemptCount', attempt_count, 'leaseGeneration', lease_generation
)::text
from requirement_matching_task where compliance_job_id='{job_id}'
order by created_at limit 1;
"""
    ).splitlines()[-1]
    return json.loads(raw)


def run_case(name: str, action: str, token: str) -> dict:
    correlation_id = f"phase4-{name}-{uuid.uuid4()}"
    orch_set_rules(
        {
            "enabled": True,
            "rules": [
                {
                    "match": {"correlationId": correlation_id},
                    "action": {"type": action, "delayMs": 1000},
                    "maxExecutions": 1,
                }
            ],
        }
    )
    # Job create uses its own correlation; we need consumer to use our correlation.
    # Outbox envelope carries correlation from create request — pass it on create.
    st, created, cid = api(
        "POST",
        f"/api/v1/tenders/{PROJECT}/compliance-analyses",
        token,
        {},
        correlation_id=correlation_id,
    )
    job_id = created["id"]
    print(name, "JOB", job_id, "cid", cid, flush=True)
    final = wait_terminal(token, job_id, 900)
    task = task_row(job_id)
    orch_set_rules({"enabled": False, "rules": []})
    return {
        "test": name,
        "action": action,
        "jobId": job_id,
        "correlationId": correlation_id,
        "finalStatus": final.get("status"),
        "task": task,
    }


def main() -> int:
    report: dict = {"startedAt": time.time(), "cases": []}
    token = login()
    timeout_case = run_case("controlled_timeout", "DELAY_THEN_TIMEOUT", token)
    unavailable_case = run_case("controlled_503", "RETURN_503", token)
    report["cases"] = [timeout_case, unavailable_case]
    timeout_ok = timeout_case["task"].get("errorCode") in {
        "MODEL_TIMEOUT",
        "LLM_TIMEOUT",
        "LLM_GENERATION_TIMEOUT",
    } or (
        timeout_case["finalStatus"] in {"FAILED", "PARTIALLY_COMPLETED"}
        and "TIMEOUT" in str(timeout_case["task"].get("errorCode") or "")
    )
    # 503 may retry then succeed; require either MODEL_UNAVAILABLE/LLM_UNAVAILABLE on task
    # or COMPLETED after retry without classifying as TIMEOUT.
    err503 = str(unavailable_case["task"].get("errorCode") or "")
    unavailable_ok = (
        err503 in {"MODEL_UNAVAILABLE", "LLM_UNAVAILABLE"}
        or (
            unavailable_case["finalStatus"] == "COMPLETED"
            and "TIMEOUT" not in err503
        )
    )
    report["timeoutPass"] = bool(timeout_ok)
    report["unavailablePass"] = bool(unavailable_ok)
    report["pass"] = report["timeoutPass"] and report["unavailablePass"]
    REPORT.write_text(json.dumps(report, indent=2, default=str))
    print("SUMMARY", json.dumps({k: report[k] for k in ("pass", "timeoutPass", "unavailablePass", "cases")}, default=str), flush=True)
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
