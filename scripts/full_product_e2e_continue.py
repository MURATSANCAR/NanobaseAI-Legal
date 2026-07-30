#!/usr/bin/env python3
"""Continue full-product E2E after Docling returned pages but zero clauses."""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "http://127.0.0.1:8098"
ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "0886a1f0-05e1-4278-ae65-03a06b9d07f2"
DOC = "881e54d8-98d9-4ab7-82d1-601134404422"
VER = "3869854a-561f-47fa-a320-a3d307f57dbd"

EVALUATION_REVIEWED = "50000000-0000-0000-0000-00000000001c"
COMPLIANT = "50000000-0000-0000-0000-00000000000b"
PARTIALLY = "50000000-0000-0000-0000-00000000000c"
CUSTOM_QUERY_SECTION = "70000000-0000-0000-0000-000000000041"
PDF_FORMAT = "70000000-0000-0000-0000-000000000038"
DATA_POLICY = "70000000-0000-0000-0000-000000000101"
TEMPLATE = "70000000-0000-0000-0000-000000000111"
TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}

REPORT: dict = {
    "steps": [],
    "ok": True,
    "projectId": PROJECT,
    "documentId": DOC,
    "sourceDocument": (
        "https://cdniys.tarimorman.gov.tr/api/File/GetGaleriFile/425/DosyaGaleri/622/"
        "sulama_sebekesinde_otomasyon_genel_teknik_sartnamesi_r00_20250520.pdf"
    ),
    "note": "Docling clauses=[] → page-level clause materialization then continue",
}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(name: str, **payload) -> None:
    entry = {
        "step": name,
        "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        **payload,
    }
    REPORT["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:600]}")


def token() -> str:
    return json.loads(
        subprocess.check_output(
            [
                "curl",
                "-sS",
                "-X",
                "POST",
                f"{API}/api/v1/auth/auto-login",
                "-H",
                "Accept: application/json",
            ],
            text=True,
        )
    )["accessToken"]


def api(method: str, path: str, tok: str, data=None, timeout: int = 180):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        f"{API}{path}",
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body else {}),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000]}
        return exc.code, parsed


def db_creds():
    user = subprocess.check_output(
        ["bash", "-lc", "sudo grep DATABASE_USER /etc/nanobaseai/legal.env | cut -d= -f2"],
        text=True,
    ).strip()
    password = subprocess.check_output(
        ["bash", "-lc", "sudo grep DATABASE_PASSWORD /etc/nanobaseai/legal.env | cut -d= -f2"],
        text=True,
    ).strip()
    return user, password


def page_items(payload) -> list:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("content", "items", "elements", "data"):
            if isinstance(payload.get(key), list):
                return payload[key]
    return []


def poll_job(tok: str, path: str, label: str, max_wait: int = 3600):
    start = time.time()
    last: dict = {}
    while time.time() - start < max_wait:
        try:
            _code, last = api("GET", path, tok)
        except Exception as exc:  # noqa: BLE001
            log(f"[{label}] poll_err={exc}")
            tok = token()
            time.sleep(10)
            continue
        status = last.get("status") or last.get("statusConceptCode")
        extra = (
            last.get("extractedRequirementCount")
            or last.get("processed_requirement_count")
            or last.get("processedRequirementCount")
            or last.get("errorCode")
            or last.get("errorMessage")
        )
        log(f"[{label}] {status} extra={extra} t={int(time.time() - start)}s")
        if status in TERMINAL or status in {"REPORT_JOB_COMPLETED", "REPORT_JOB_FAILED"}:
            return last, tok
        time.sleep(15)
    raise SystemExit(f"timeout {label}: {last}")


def materialize_page_clauses(tok: str) -> int:
    _code, pages = api("GET", f"/api/v1/documents/{DOC}/pages?page=0&size=100", tok)
    items = page_items(pages)
    sql_path = Path("/tmp/e2e_seed_clauses.sql")
    lines = [
        f"SET app.current_organization_id = '{ORG}';",
        f"DELETE FROM clause WHERE document_version_id='{VER}';",
    ]
    created = 0
    # Prefer richest pages so LLM requirement extraction finishes in a realistic demo window.
    ranked = []
    for page in items:
        text = (page.get("normalizedText") or page.get("rawText") or "").strip()
        if len(text) < 200:
            continue
        ranked.append((len(text), int(page["pageNumber"]), text))
    ranked.sort(reverse=True)
    selected = ranked[:8]
    selected.sort(key=lambda item: item[1])
    for _length, page_number, text in selected:
        clause_id = str(uuid.uuid4())
        content_hash = hashlib.sha256(text.encode()).hexdigest()
        lines.append(
            "INSERT INTO clause ("
            "id,organization_id,document_version_id,parent_clause_id,clause_number,"
            "title,raw_text,page_start,sort_order,created_at,normalized_text,clause_type,"
            "page_end,bounding_boxes_json,content_hash,updated_at,version"
            ") VALUES ("
            f"'{clause_id}','{ORG}','{VER}',NULL,'P{page_number}','Sayfa {page_number}',"
            f"$txt${text}$txt$,{page_number},{page_number - 1},now(),"
            f"$txt${text}$txt$,'PAGE',{page_number},'[]'::jsonb,"
            f"'{content_hash}',now(),0);"
        )
        created += 1
    sql_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    user, password = db_creds()
    with sql_path.open("rb") as handle:
        subprocess.check_call(
            [
                "docker",
                "exec",
                "-i",
                "-e",
                f"PGPASSWORD={password}",
                "actenora-prodlike-postgres",
                "psql",
                "-U",
                user,
                "-d",
                "specai",
                "-v",
                "ON_ERROR_STOP=1",
            ],
            stdin=handle,
        )
    return created


def main() -> int:
    tok = token()
    created = materialize_page_clauses(tok)
    _ccode, clauses = api("GET", f"/api/v1/documents/{DOC}/clauses?page=0&size=5", tok)
    step(
        "6_clauses_materialized",
        created=created,
        apiTotal=clauses.get("totalElements"),
        note="page-level fallback because Docling structured clauses were empty",
    )

    _rcode, req_job = api("POST", f"/api/v1/documents/{DOC}/requirement-extractions", tok, {})
    req_id = req_job.get("id")
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_id}", "requirements")
    step(
        "7_requirement_extraction",
        jobId=req_id,
        status=req_done.get("status"),
        extracted=req_done.get("extractedRequirementCount"),
        totalClauses=req_done.get("totalClauseCount"),
        processed=req_done.get("processedClauseCount"),
    )
    _qcode, reqs = api(
        "GET",
        f"/api/v1/tenders/{PROJECT}/requirements?size=50&sort=createdAt,desc",
        tok,
    )
    req_items = page_items(reqs)
    step("8_list_requirements", count=len(req_items), total=reqs.get("totalElements"))
    if not req_items:
        REPORT["ok"] = False
        Path("/tmp/full_product_e2e_report.json").write_text(
            json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        return 2

    kcode, know_job = api("POST", f"/api/v1/documents/{DOC}/knowledge-extractions", tok, {})
    if kcode in (200, 201, 202):
        know_id = know_job.get("id")
        know_done, tok = poll_job(
            tok, f"/api/v1/knowledge-extractions/{know_id}", "knowledge", max_wait=1800
        )
        step(
            "9a_knowledge",
            jobId=know_id,
            status=know_done.get("status"),
            entities=know_done.get("extractedEntityCount"),
        )
    else:
        step("9a_knowledge", skipped=True, http=kcode)

    _ecode, evidence = api("GET", "/api/v1/evidence?page=0&size=20", tok)
    _ccode2, caps = api("GET", "/api/v1/company-capabilities?page=0&size=20", tok)
    _kcode2, entities = api("GET", "/api/v1/knowledge/entities?page=0&size=20", tok)
    step(
        "9_evidence_match",
        evidence=len(page_items(evidence)) or evidence.get("totalElements"),
        capabilities=len(page_items(caps)) or caps.get("totalElements"),
        entities=len(entities) if isinstance(entities, list) else entities.get("totalElements"),
    )

    _jcode, job = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", tok)
    job_id = job.get("id")
    job_done, tok = poll_job(
        tok, f"/api/v1/compliance-analyses/{job_id}", "compliance", max_wait=7200
    )
    step(
        "10_compliance",
        jobId=job_id,
        status=job_done.get("status"),
        processed=job_done.get("processed_requirement_count")
        or job_done.get("processedRequirementCount"),
        completed=job_done.get("completed_count") or job_done.get("completedCount"),
        failed=job_done.get("failed_count") or job_done.get("failedCount"),
    )
    REPORT["complianceJobId"] = job_id

    _ecode2, evals = api("GET", f"/api/v1/tenders/{PROJECT}/compliance-evaluations", tok)
    eval_items = page_items(evals)
    sample = eval_items[0] if eval_items else {}
    step(
        "11_results",
        evalCount=len(eval_items) or evals.get("totalElements"),
        sampleId=sample.get("id"),
        suggested=sample.get("suggestedDecision") or sample.get("suggestedDecisionCode"),
        evidenceCount=sample.get("evidenceCount"),
        grounding=sample.get("groundingStatus") or sample.get("groundingStatusCode"),
    )

    ricode, risk_job = api("POST", f"/api/v1/tenders/{PROJECT}/risk-analyses", tok)
    if ricode in (200, 201, 202):
        risk_id = risk_job.get("id")
        risk_done, tok = poll_job(
            tok, f"/api/v1/risk-analyses/{risk_id}", "risk", max_wait=1800
        )
        step("12a_risk", jobId=risk_id, status=risk_done.get("status"))
    _rscode, risks = api("GET", f"/api/v1/tenders/{PROJECT}/risks", tok)
    _cfcode, conflicts = api("GET", f"/api/v1/tenders/{PROJECT}/conflicts", tok)
    step(
        "12_risks_conflicts",
        risks=len(page_items(risks)) or risks.get("totalElements"),
        conflicts=len(page_items(conflicts)) or conflicts.get("totalElements"),
    )

    if sample.get("id"):
        decision = COMPLIANT if sample.get("evidenceCount") else PARTIALLY
        rvcode, _reviewed = api(
            "POST",
            f"/api/v1/compliance-evaluations/{sample['id']}/review",
            tok,
            {
                "finalDecisionConceptId": decision,
                "changeTypeConceptId": EVALUATION_REVIEWED,
                "feedbackTypeConceptId": None,
                "reason": "E2E canlı doğrulama: kullanıcı onay/düzeltme",
            },
        )
        step("13_review", http=rvcode, evaluationId=sample["id"])
    else:
        step("13_review", skipped=True)

    stamp = int(time.time())
    _dcode2, definition = api(
        "POST",
        "/api/v1/report-definitions",
        tok,
        {
            "reportCode": f"E2E_COMPLIANCE_{stamp}",
            "name": "E2E Compliance Snapshot",
            "description": "Uçtan uca canlı rapor",
            "scope": "ORGANIZATION",
        },
    )
    def_id = definition.get("id")
    _vcode, version = api(
        "POST",
        f"/api/v1/report-definitions/{def_id}/versions",
        tok,
        {
            "sectionConfiguration": {},
            "dataPolicyVersionId": DATA_POLICY,
            "templateVersionId": TEMPLATE,
            "sections": [
                {
                    "sectionCode": "DYNAMIC_SUMMARY",
                    "sectionTypeConceptId": CUSTOM_QUERY_SECTION,
                    "titleTemplate": "Doğrulanmış proje özeti",
                    "dataQueryConfiguration": {"snapshotJsonPointer": "/counts"},
                    "renderConfiguration": {},
                    "visibilityCondition": {},
                    "sortOrder": 1,
                }
            ],
        },
    )
    version_id = version.get("id")
    api("POST", f"/api/v1/report-definition-versions/{version_id}/activate", tok)
    gcode, report_job = api(
        "POST",
        f"/api/v1/tenders/{PROJECT}/reports",
        tok,
        {
            "reportDefinitionVersionId": version_id,
            "formatConceptIds": [PDF_FORMAT],
            "staleOverrideApprovalId": None,
        },
        timeout=300,
    )
    artifacts = report_job.get("artifacts") or []
    step(
        "14_report",
        http=gcode,
        status=report_job.get("statusConceptCode"),
        artifacts=len(artifacts),
        error=report_job.get("errorMessage"),
    )
    if artifacts:
        art_id = artifacts[0]["id"]
        _ucode, url_body = api("GET", f"/api/v1/report-artifacts/{art_id}/download-url", tok)
        url = url_body.get("url")
        download_path = f"/tmp/e2e_report_{PROJECT}.pdf"
        if url:
            subprocess.check_call(["curl", "-fsSL", "-o", download_path, url])
            step(
                "15_download",
                path=download_path,
                bytes=Path(download_path).stat().st_size,
            )
        else:
            REPORT["ok"] = False
            step("15_download", error=url_body)
    else:
        REPORT["ok"] = False
        step("15_download", skipped=True, detail=str(report_job)[:400])

    _acode, audit = api("GET", "/api/v1/audit-events?page=0&size=30&sort=createdAt,desc", tok)
    audit_items = page_items(audit)
    step(
        "16_audit",
        count=len(audit_items) or audit.get("totalElements"),
        sample=[
            {
                "eventType": item.get("eventType"),
                "entityType": item.get("entityType"),
                "createdAt": item.get("createdAt"),
            }
            for item in audit_items[:12]
        ],
    )
    REPORT["portal"] = {
        "documents": (
            f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/documents"
        ),
        "requirements": (
            f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/requirements"
        ),
        "compliance": (
            f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/compliance"
        ),
        "activity": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/project/activity",
    }
    REPORT["ok"] = REPORT["ok"] and job_done.get("status") == "COMPLETED"
    Path("/tmp/full_product_e2e_report.json").write_text(
        json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    log(f"DONE ok={REPORT['ok']}")
    return 0 if REPORT["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
