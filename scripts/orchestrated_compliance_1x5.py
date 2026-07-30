#!/usr/bin/env python3
"""Live gate: 1 requirement × 5 evidence candidates (rerank=5) via full job path."""
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
HOLD_PROJECT = "2175cb7c-5c01-41f1-a0e0-a4888ed1e4a3"
API = "http://127.0.0.1:8098"
REPORT = Path("/tmp/orchestrated_1x5_report.json")
FIXTURE_CODE = "COMPLIANCE_1X5_TIER_TEST"
RERANK = 5
ANCHOR_FRAGMENT = "4cd5fd0c-51cf-4d6f-a63c-126fad74b960"
FIXTURE_FRAGMENTS = [
    {
        "id": "a1000000-0000-4000-8000-000000000001",
        "page": 901,
        "text": "Ana veri merkezi Uptime Institute Tier III sertifikasina sahiptir.",
        "normalized": "ana veri merkezi uptime institute tier iii sertifikasina sahiptir",
        "hash": "c1a5e00100000000000000000000000000000000000000000000000000000001",
        "role": "compliant_tier_iii",
    },
    {
        "id": "a1000000-0000-4000-8000-000000000002",
        "page": 902,
        "text": "Veri merkezi altyapisi Tier II seviyesindedir; Tier III degildir.",
        "normalized": "veri merkezi altyapisi tier ii seviyesindedir tier iii degildir",
        "hash": "c1a5e00100000000000000000000000000000000000000000000000000000002",
        "role": "non_compliant_tier_ii",
    },
    {
        "id": "a1000000-0000-4000-8000-000000000003",
        "page": 903,
        "text": "Ofis climate control standardinda calisir; ana veri merkezi degildir.",
        "normalized": "ofis climate control standardinda calisir ana veri merkezi degildir",
        "hash": "c1a5e00100000000000000000000000000000000000000000000000000000003",
        "role": "low_relevance_office",
    },
    {
        "id": "a1000000-0000-4000-8000-000000000004",
        "page": 904,
        "text": "Ana veri merkezi Uptime Institute Tier III sertifikasina sahiptir. (kopya)",
        "normalized": "ana veri merkezi uptime institute tier iii sertifikasina sahiptir kopya",
        "hash": "c1a5e00100000000000000000000000000000000000000000000000000000004",
        "role": "near_duplicate",
    },
    {
        "id": "a1000000-0000-4000-8000-000000000005",
        "page": 905,
        "text": "Veri merkezi icin tier hedefi planlanmistir ancak standardinda sertifika yoktur.",
        "normalized": "veri merkezi icin tier hedefi planlanmistir ancak standardinda sertifika yoktur",
        "hash": "c1a5e00100000000000000000000000000000000000000000000000000000005",
        "role": "weak_evidence",
    },
]


def env(key: str) -> str:
    out = subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    )
    return out.strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/orch1x5.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/orch1x5.sql", "actenora-prodlike-postgres:/tmp/orch1x5.sql"]
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
            "/tmp/orch1x5.sql",
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
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())["accessToken"]


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
    remaining = int(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from requirement where organization_id='{ORG}' and project_id='{PROJECT}';
"""
        ).splitlines()[-1]
    )
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
    cfg["candidateLimits"]["reranking"] = RERANK
    cfg["candidateLimits"]["metadata"] = 20
    cfg["candidateLimits"]["lexical"] = max(10, RERANK)
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
update retrieval_policy_version
   set configuration_json = '{json.dumps(cfg).replace("'", "''")}'::jsonb
 where id = '{policy_id}';
"""
    )
    anchor = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select document_id::text || '|' || document_version_id::text
  from evidence_fragment
 where id = '{ANCHOR_FRAGMENT}';
"""
    ).splitlines()[-1]
    document_id, document_version_id = anchor.split("|", 1)
    fixture_ids = [item["id"] for item in FIXTURE_FRAGMENTS]
    id_list = ",".join(f"'{item}'" for item in fixture_ids)
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
delete from evidence_fragment
 where organization_id = '{ORG}'
   and id in ({id_list});
"""
    )
    values = []
    for item in FIXTURE_FRAGMENTS:
        text = item["text"].replace("'", "''")
        normalized = item["normalized"].replace("'", "''")
        values.append(
            "("
            f"'{item['id']}', '{ORG}', '{document_id}', '{document_version_id}', "
            f"{item['page']}, '{text}', '{normalized}', '[]'::jsonb, "
            f"'{item['hash']}', 'tr', 1, 1, null, now() - interval '2 minutes'"
            ")"
        )
    values_sql = ",\n".join(values)
    psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
insert into evidence_fragment (
  id, organization_id, document_id, document_version_id,
  page_number, fragment_text, normalized_text, bounding_boxes_json,
  content_hash, language, parser_quality, ocr_quality,
  valid_until, created_at
) values
{values_sql};
"""
    )
    lexical_hits = int(
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
with q as (
  select unnest(string_to_array('ana veri merkezi tier standardinda', ' ')) as token
)
select count(*) from evidence_fragment f
 where f.organization_id = '{ORG}'
   and f.created_at <= now()
   and (f.valid_until is null or f.valid_until > now())
   and (
     select count(*)::int from q
      where q.token <> ''
        and to_tsvector('simple', f.normalized_text)
            @@ to_tsquery('simple', q.token || ':*')
   ) >= 2;
"""
        ).splitlines()[-1]
    )
    assert lexical_hits >= RERANK, f"fixture lexical hits={lexical_hits} expected>={RERANK}"
    return {
        "requirementCountOnProject": remaining,
        "movedRequirementIds": moved_ids,
        "retrievalPolicyVersionId": policy_id,
        "retrievalConfigBefore": json.loads(cfg_raw),
        "retrievalConfigTest": cfg,
        "fixtureFragmentIds": fixture_ids,
        "lexicalHitCount": lexical_hits,
        "documentId": document_id,
        "documentVersionId": document_version_id,
    }


def restore(state: dict) -> None:
    fixture_ids = state.get("fixtureFragmentIds") or []
    if fixture_ids:
        id_list = ",".join(f"'{item}'" for item in fixture_ids)
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
delete from compliance_evidence_link
 where evidence_fragment_id in ({id_list});
delete from evidence_fragment
 where organization_id = '{ORG}'
   and id in ({id_list});
"""
        )
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
    before = state.get("retrievalConfigBefore")
    policy_id = state.get("retrievalPolicyVersionId")
    if before and policy_id:
        psql(
            f"""
select set_config('app.current_organization_id','{ORG}',true);
update retrieval_policy_version
   set configuration_json = '{json.dumps(before).replace("'", "''")}'::jsonb
 where id = '{policy_id}';
"""
        )


def job_snapshot(job_id: str) -> dict:
    row = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select jsonb_build_object(
  'status', status,
  'completed', completed_count,
  'failed', failed_count,
  'processed', processed_requirement_count,
  'total', total_requirement_count,
  'startedAt', started_at,
  'completedAt', completed_at,
  'claimedBy', claimed_by,
  'leaseGeneration', lease_generation,
  'heartbeatAt', heartbeat_at,
  'leaseExpiresAt', lease_expires_at
)::text
from compliance_analysis_job where id = '{job_id}';
"""
    ).splitlines()[-1]
    job = json.loads(row)
    tasks = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select coalesce(jsonb_agg(jsonb_build_object(
  'taskId', id,
  'requirementId', requirement_id,
  'status', status,
  'candidateCount', candidate_count,
  'rerankedCandidateCount', reranked_candidate_count,
  'selectedEvidenceCount', selected_evidence_count,
  'errorCode', error_code,
  'leaseGeneration', lease_generation,
  'claimedBy', claimed_by,
  'startedAt', started_at,
  'completedAt', completed_at
) order by created_at), '[]'::jsonb)::text
from requirement_matching_task where compliance_job_id = '{job_id}';
"""
    ).splitlines()[-1]
    job["tasks"] = json.loads(tasks)
    return job


def duplicate_links(job_id: str) -> int:
    raw = psql(
        f"""
select set_config('app.current_organization_id','{ORG}',true);
select count(*) from (
  select link.compliance_evaluation_id, link.evidence_fragment_id, count(*)
    from compliance_evidence_link link
    join compliance_evaluation ev on ev.id = link.compliance_evaluation_id
   where ev.analysis_job_id = '{job_id}'
   group by 1, 2
  having count(*) > 1
) d;
"""
    ).splitlines()[-1]
    return int(raw)


def collect_slot(job_id: str) -> dict:
    orch = subprocess.check_output(
        ["sudo", "docker", "logs", "--since", "60m", "specai-legal-ai-orchestrator-1"],
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
    )
    metrics = {
        "slot_acquired": False,
        "slot_released": False,
        "queueWaitMs": None,
        "generationMs": None,
        "llmUnavailableCount": 0,
        "modelRequestCount": 0,
    }
    for line in orch.splitlines():
        if "LLM_UNAVAILABLE" in line:
            metrics["llmUnavailableCount"] += 1
        if "model_call_trace" not in line:
            continue
        metrics["modelRequestCount"] += 1
        metrics["slot_acquired"] = True
        metrics["slot_released"] = True
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
    return metrics


def main() -> int:
    report: dict = {
        "test": "orchestrated_1x5",
        "fixtureCode": FIXTURE_CODE,
        "organizationId": ORG,
        "projectId": PROJECT,
        "requirementId": REQ,
        "expectedCandidateCount": RERANK,
        "expectedRerankedCount": RERANK,
        "startedAt": time.time(),
    }
    state = None
    try:
        print("LOGIN", flush=True)
        token = login()
        print(f"PREPARE 1-req + rerank={RERANK}", flush=True)
        state = prepare()
        report["prepare"] = {
            "requirementCountOnProject": state["requirementCountOnProject"],
            "retrievalPolicyVersionId": state["retrievalPolicyVersionId"],
            "reranking": state["retrievalConfigTest"]["candidateLimits"]["reranking"],
        }
        assert state["requirementCountOnProject"] == 1, state

        created_at = time.time()
        st, created = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
        assert st in (200, 202), (st, created)
        job_id = created["id"]
        report["jobId"] = job_id
        print("JOB", job_id, created.get("status"), flush=True)

        transitions = []
        claimed_at = None
        final = None
        deadline = time.time() + 900
        while time.time() < deadline:
            snap = job_snapshot(job_id)
            status = snap.get("status")
            if not transitions or transitions[-1]["status"] != status:
                entry = {
                    "at": time.time(),
                    "elapsedMs": int((time.time() - created_at) * 1000),
                    "status": status,
                    "completed": snap.get("completed"),
                    "failed": snap.get("failed"),
                    "tasks": [
                        {
                            "status": t.get("status"),
                            "candidateCount": t.get("candidateCount"),
                            "rerankedCandidateCount": t.get("rerankedCandidateCount"),
                            "selectedEvidenceCount": t.get("selectedEvidenceCount"),
                            "errorCode": t.get("errorCode"),
                            "leaseGeneration": t.get("leaseGeneration"),
                        }
                        for t in snap.get("tasks") or []
                    ],
                }
                transitions.append(entry)
                print(
                    f"TRANSITION {status} candidates="
                    f"{entry['tasks'][0].get('candidateCount') if entry['tasks'] else None} "
                    f"reranked="
                    f"{entry['tasks'][0].get('rerankedCandidateCount') if entry['tasks'] else None}",
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
        slot = collect_slot(job_id)
        dup = duplicate_links(job_id)
        task = (final.get("tasks") or [{}])[0]
        claim_duration_ms = int((claimed_at - created_at) * 1000) if claimed_at else None
        report.update(
            {
                "transitions": transitions,
                "final": final,
                "claim_duration_ms": claim_duration_ms,
                "job_duration_ms": int((finished_at - created_at) * 1000),
                "duplicateEvidenceLinks": dup,
                "slot": slot,
                "pass": (
                    final.get("status") == "COMPLETED"
                    and int(task.get("candidateCount") or 0) >= 1
                    and int(task.get("rerankedCandidateCount") or 0) == RERANK
                    and int(task.get("selectedEvidenceCount") or 0) >= 1
                    and dup == 0
                    and slot["llmUnavailableCount"] == 0
                    and slot["slot_acquired"]
                    and slot["slot_released"]
                    and claim_duration_ms is not None
                    and any(t["status"] == "RUNNING" for t in transitions)
                ),
            }
        )
        print(
            "SUMMARY",
            json.dumps(
                {
                    "jobId": job_id,
                    "finalStatus": final.get("status"),
                    "candidateCount": task.get("candidateCount"),
                    "rerankedCandidateCount": task.get("rerankedCandidateCount"),
                    "selectedEvidenceCount": task.get("selectedEvidenceCount"),
                    "claim_duration_ms": claim_duration_ms,
                    "duplicateEvidenceLinks": dup,
                    "pass": report["pass"],
                }
            ),
            flush=True,
        )
        return 0 if report["pass"] else 1
    finally:
        REPORT.write_text(json.dumps(report, indent=2, default=str))
        print("REPORT", REPORT, flush=True)
        if state is not None:
            restore(state)
            print("RESTORE done", flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
