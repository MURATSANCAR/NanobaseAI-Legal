#!/usr/bin/env python3
"""Quick MODEL_TIMEOUT domain-code retest after mapping fix."""
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


def api(method, path, token, body=None, cid=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Organization-Id": ORG,
            "X-Correlation-ID": cid or str(uuid.uuid4()),
        },
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def psql(sql: str) -> str:
    Path("/tmp/mt.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/mt.sql", "actenora-prodlike-postgres:/tmp/mt.sql"]
    )
    return subprocess.check_output(
        [
            "sudo", "docker", "exec", "-e", f"PGPASSWORD={env('DATABASE_PASSWORD')}",
            "actenora-prodlike-postgres", "psql", "-U", env("DATABASE_USER"), "-d", "specai",
            "-At", "-f", "/tmp/mt.sql",
        ],
        text=True,
    ).strip()


def main() -> int:
    token = login()
    fault = env("AI_ORCHESTRATOR_FAULT_INJECTION_TOKEN")
    cid = str(uuid.uuid4())
    rules = json.dumps(
        {
            "enabled": True,
            "rules": [
                {
                    "match": {"correlationId": cid},
                    "action": {"type": "DELAY_THEN_TIMEOUT", "delayMs": 500},
                    "maxExecutions": 1,
                }
            ],
        }
    )
    subprocess.check_call(
        [
            "sudo", "docker", "exec", "specai-legal-ai-orchestrator-1", "python", "-c",
            "import urllib.request; "
            f"req=urllib.request.Request('http://127.0.0.1:8090/v1/test/fault-injection/rules',"
            f"data={rules!r}.encode(),method='POST',"
            f"headers={{'Content-Type':'application/json','X-Fault-Injection-Token':{fault!r}}});"
            "print(urllib.request.urlopen(req,timeout=30).read().decode())",
        ]
    )
    job = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {}, cid)
    job_id = job["id"]
    print("JOB", job_id, flush=True)
    snap = None
    for _ in range(180):
        snap = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
        if snap.get("status") in {"FAILED", "COMPLETED", "PARTIALLY_COMPLETED", "CANCELLED"}:
            break
        time.sleep(1)
    task = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(error_code,'null') || '|' || status
  from requirement_matching_task where compliance_job_id='{job_id}' limit 1;
"""
    ).splitlines()[-1]
    print("RESULT", {"final": snap.get("status"), "task": task}, flush=True)
    subprocess.check_call(
        [
            "sudo", "docker", "exec", "specai-legal-ai-orchestrator-1", "python", "-c",
            "import urllib.request; "
            "req=urllib.request.Request('http://127.0.0.1:8090/v1/test/fault-injection/rules',"
            "data=b'{\"enabled\":false,\"rules\":[]}',method='POST',"
            f"headers={{'Content-Type':'application/json','X-Fault-Injection-Token':{fault!r}}});"
            "print(urllib.request.urlopen(req,timeout=30).read().decode())",
        ]
    )
    code = task.split("|", 1)[0]
    ok = code == "MODEL_TIMEOUT" and snap.get("status") in {"FAILED", "PARTIALLY_COMPLETED"}
    Path("/tmp/phase4_model_timeout_retest.json").write_text(
        json.dumps({"pass": ok, "jobId": job_id, "task": task, "final": snap.get("status")}, indent=2)
    )
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
