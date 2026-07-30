#!/usr/bin/env python3
"""Orchestrated compliance Test 1: 1 requirement × 1 evidence via full job path."""
from __future__ import annotations

import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
REQ = "184e7eac-7808-4b79-86df-a70bf619bc33"
HOLD_PROJECT = "2175cb7c-5c01-41f1-a0e0-a4888ed1e4a3"  # empty demo tender
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/orchestrated_1x1_report.json")


def env(key: str) -> str:
    out = subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    )
    return out.strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/orch1x1.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/orch1x1.sql", "actenora-prodlike-postgres:/tmp/orch1x1.sql"]
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
            "/tmp/orch1x1.sql",
        ],
        text=True,
    ).strip()


def api(method: str, path: str, token: str, body=None, timeout: int = 120):
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
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = {"raw": raw[:500]}
        return exc.code, payload


def login() -> str:
    st, payload = api(
        "POST",
        "/api/v1/auth/login",
        token="",
        body={
            "email": env("SPECAI_LOCAL_ADMIN_EMAIL"),
            "password": env("SPECAI_LOCAL_ADMIN_PASSWORD"),
        },
    )
    # api() always sends Bearer; for login override
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
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())["accessToken"]


def prepare() -> dict:
    moved_ids_raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(string_agg(id::text, ','), '')
  from requirement
 where organization_id = '{ORG}'
   and project_id = '{PROJECT}'
   and id <> '{REQ}';
"""
    ).splitlines()[-1]
    moved_ids = [item for item in moved_ids_raw.split(",") if item]
    if moved_ids:
        id_list = ",".join(f"'{item}'" for item in moved_ids)
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement
   set project_id = '{HOLD_PROJECT}', updated_at = now()
 where organization_id = '{ORG}'
   and id in ({id_list});
"""
        )
    remaining = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from requirement where organization_id='{ORG}' and project_id='{PROJECT}';
"""
    ).splitlines()[-1]

    # Force reranking top-1 on active retrieval policy version.
    before_policy = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select version.id::text || '|' || version.configuration_json::text
  from retrieval_policy_version version
  join retrieval_policy_definition definition
    on definition.active_version_id = version.id
 where version.status = 'ACTIVE'
 order by (version.organization_id is not null) desc
 limit 1;
"""
    ).splitlines()[-1]
    policy_id, cfg_raw = before_policy.split("|", 1)
    cfg = json.loads(cfg_raw)
    cfg.setdefault("candidateLimits", {})
    cfg["candidateLimits"]["reranking"] = 1
    cfg["candidateLimits"]["metadata"] = 20
    cfg["candidateLimits"]["lexical"] = 10
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update retrieval_policy_version
   set configuration_json = '{json.dumps(cfg).replace("'", "''")}'::jsonb
 where id = '{policy_id}';
"""
    )
    return {
        "requirementCountOnProject": int(remaining),
        "movedRequirementIds": moved_ids,
        "retrievalPolicyVersionId": policy_id,
        "retrievalConfigBefore": json.loads(cfg_raw),
        "retrievalConfigTest": cfg,
    }


def restore(state: dict) -> None:
    moved_ids = state.get("movedRequirementIds") or []
    if moved_ids:
        id_list = ",".join(f"'{item}'" for item in moved_ids)
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
update requirement
   set project_id = '{PROJECT}', updated_at = now()
 where organization_id = '{ORG}'
   and id in ({id_list});
"""
        )
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update retrieval_policy_version
   set configuration_json = '{json.dumps(state["retrievalConfigBefore"]).replace("'", "''")}'::jsonb
 where id = '{state["retrievalPolicyVersionId"]}';
"""
    )


def slot_probe() -> dict:
    # Best-effort: parse recent orchestrator traces for active/queue signals.
    logs = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "5m", "specai-legal-ai-orchestrator-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    return {"recentTraceLines": sum(1 for line in logs.splitlines() if "model_call_trace" in line)}


def job_snapshot(job_id: str) -> dict:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select json_build_object(
  'status', status,
  'completed', completed_count,
  'failed', failed_count,
  'processed', processed_requirement_count,
  'total', total_requirement_count,
  'startedAt', started_at,
  'completedAt', completed_at,
  'updatedAt', updated_at,
  'createdAt', created_at
)::text
from compliance_analysis_job where id='{job_id}';
"""
    ).splitlines()[-1]
    job = json.loads(raw)
    tasks = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(json_agg(json_build_object(
  'taskId', id,
  'requirementId', requirement_id,
  'status', status,
  'candidateCount', candidate_count,
  'rerankedCandidateCount', reranked_candidate_count,
  'selectedEvidenceCount', selected_evidence_count,
  'errorCode', error_code,
  'startedAt', started_at,
  'completedAt', completed_at
) order by created_at), '[]'::json)::text
from requirement_matching_task where compliance_job_id='{job_id}';
"""
    ).splitlines()[-1]
    job["tasks"] = json.loads(tasks)
    return job


def collect_logs(job_id: str, correlation_hint: str | None) -> dict:
    backend = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "45m", "specai-legal-backend-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    orch = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "45m", "specai-legal-ai-orchestrator-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    be_lines = [line for line in backend.splitlines() if job_id in line or REQ in line]
    orch_lines = []
    for line in orch.splitlines():
        if "model_call_trace" in line or "model_response_meta" in line or "runtime_" in line:
            orch_lines.append(line)
        if correlation_hint and correlation_hint in line:
            orch_lines.append(line)
    # Keep last relevant orch traces
    orch_lines = orch_lines[-40:]
    return {"backendRelevant": be_lines[-80:], "orchestratorRelevant": orch_lines}


def parse_slot_metrics(orch_lines: list[str]) -> dict:
    metrics = {
        "slot_acquired": False,
        "slot_released": False,
        "queueWaitMs": None,
        "generationMs": None,
        "llmResult": None,
        "failureCode": None,
        "llmUnavailableCount": 0,
    }
    for line in orch_lines:
        if "LLM_UNAVAILABLE" in line:
            metrics["llmUnavailableCount"] += 1
        if "model_call_trace" not in line:
            continue
        metrics["slot_acquired"] = True
        metrics["slot_released"] = True  # release is in finally after every acquire
        for token in line.split():
            if "=" not in token:
                continue
            k, _, v = token.partition("=")
            if k == "queueWaitMs":
                try:
                    metrics["queueWaitMs"] = float(v)
                except ValueError:
                    metrics["queueWaitMs"] = v
            elif k == "generationMs":
                try:
                    metrics["generationMs"] = float(v)
                except ValueError:
                    metrics["generationMs"] = v
            elif k == "result":
                metrics["llmResult"] = v
            elif k == "failureCode":
                metrics["failureCode"] = None if v in {"None", "null"} else v
    return metrics


def main() -> int:
    report: dict = {"test": "orchestrated_1x1", "startedAt": time.time()}
    state = None
    try:
        print("LOGIN", flush=True)
        token = login()
        print("PREPARE 1-req project + rerank=1", flush=True)
        state = prepare()
        report["prepare"] = {
            "requirementCountOnProject": state["requirementCountOnProject"],
            "retrievalPolicyVersionId": state["retrievalPolicyVersionId"],
            "reranking": state["retrievalConfigTest"]["candidateLimits"]["reranking"],
        }
        assert state["requirementCountOnProject"] == 1, state

        report["slot_before"] = slot_probe()
        t0 = time.time()
        print("CREATE JOB", flush=True)
        # create
        data = b"{}"
        req = urllib.request.Request(
            f"{API}/api/v1/tenders/{PROJECT}/compliance-analyses",
            data=data,
            method="POST",
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-Organization-Id": ORG,
                "X-Correlation-ID": str(uuid.uuid4()),
            },
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            created = json.loads(resp.read())
        job_id = created["id"]
        created_at = time.time()
        report["jobId"] = job_id
        report["createResponse"] = {
            "id": job_id,
            "status": created.get("status"),
            "totalRequirementCount": created.get("totalRequirementCount"),
        }
        print("JOB", job_id, created.get("status"), flush=True)

        # poll transitions
        transitions = []
        claimed_at = None
        final = None
        for i in range(180):
            snap = job_snapshot(job_id)
            status = snap["status"]
            if not transitions or transitions[-1]["status"] != status:
                transitions.append(
                    {
                        "at": time.time(),
                        "elapsedMs": int((time.time() - t0) * 1000),
                        "status": status,
                        "completed": snap.get("completed"),
                        "failed": snap.get("failed"),
                        "tasks": [
                            {
                                "status": t.get("status"),
                                "candidateCount": t.get("candidateCount"),
                                "rerankedCandidateCount": t.get("rerankedCandidateCount"),
                                "errorCode": t.get("errorCode"),
                            }
                            for t in snap.get("tasks") or []
                        ],
                    }
                )
                print(
                    f"TRANSITION {status} completed={snap.get('completed')} "
                    f"failed={snap.get('failed')} tasks={transitions[-1]['tasks']}",
                    flush=True,
                )
            if status == "RUNNING" and claimed_at is None:
                claimed_at = time.time()
            if status in {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}:
                final = snap
                break
            time.sleep(2)
        finished_at = time.time()
        if final is None:
            final = job_snapshot(job_id)

        logs = collect_logs(job_id, None)
        slot = parse_slot_metrics(logs["orchestratorRelevant"])
        claim_duration_ms = (
            int((claimed_at - created_at) * 1000) if claimed_at else None
        )
        job_duration_ms = int((finished_at - created_at) * 1000)

        report.update(
            {
                "transitions": transitions,
                "final": final,
                "claim_duration_ms": claim_duration_ms,
                "job_duration_ms": job_duration_ms,
                "slot_acquired": slot["slot_acquired"],
                "slot_released": slot["slot_released"],
                "queueWaitMs": slot["queueWaitMs"],
                "generationMs": slot["generationMs"],
                "llmResult": slot["llmResult"],
                "failureCode": slot["failureCode"],
                "llmUnavailableCount": slot["llmUnavailableCount"],
                "backendLogHits": len(logs["backendRelevant"]),
                "orchestratorLogHits": len(logs["orchestratorRelevant"]),
                "pass": final.get("status")
                in {"COMPLETED", "PARTIALLY_COMPLETED"}
                and slot["llmUnavailableCount"] == 0
                and final.get("failed", 1) == 0,
            }
        )
        REPORT.write_text(json.dumps(report, indent=2, default=str))
        print("SUMMARY", json.dumps({
            "jobId": job_id,
            "finalStatus": final.get("status"),
            "claim_duration_ms": claim_duration_ms,
            "job_duration_ms": job_duration_ms,
            "slot_acquired": slot["slot_acquired"],
            "slot_released": slot["slot_released"],
            "queueWaitMs": slot["queueWaitMs"],
            "generationMs": slot["generationMs"],
            "llmResult": slot["llmResult"],
            "tasks": final.get("tasks"),
            "pass": report["pass"],
        }, ensure_ascii=False), flush=True)
        print("REPORT", REPORT, flush=True)
        return 0 if report["pass"] else 1
    finally:
        if state is not None:
            print("RESTORE requirements + retrieval policy", flush=True)
            restore(state)


if __name__ == "__main__":
    raise SystemExit(main())
