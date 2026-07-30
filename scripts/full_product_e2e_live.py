#!/usr/bin/env python3
"""Full product E2E against live EasyMeeting deploy (API = UI journey).

Flow: login → project → upload real TR teknik şartname → MinIO/parser/OCR →
clauses → requirements → knowledge/evidence → compliance → review →
risk/conflict → report download → audit.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta
from pathlib import Path

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
PDF_PATH = Path(
    os.environ.get(
        "E2E_PDF",
        "/tmp/nanobase-e2e/DSI_Sulama_Otomasyon_Genel_Teknik_Sartname.pdf",
    )
)
TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}
DOC_READY = {"READY"}
DOC_FAIL = {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}

# Ontology bootstrap IDs
EVALUATION_REVIEWED = "50000000-0000-0000-0000-00000000001c"
COMPLIANT = "50000000-0000-0000-0000-00000000000b"
PARTIALLY = "50000000-0000-0000-0000-00000000000c"
CUSTOM_QUERY_SECTION = "70000000-0000-0000-0000-000000000041"
PDF_FORMAT = "70000000-0000-0000-0000-000000000038"
DATA_POLICY = "70000000-0000-0000-0000-000000000101"
TEMPLATE = "70000000-0000-0000-0000-000000000111"

REPORT: dict = {"steps": [], "ok": True}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(name: str, **payload) -> None:
    entry = {"step": name, "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), **payload}
    REPORT["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:500]}")


def token() -> str:
    body = json.loads(
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
    )
    return body["accessToken"]


def api(method: str, path: str, tok: str, data=None, timeout: int = 180):
    url = path if path.startswith("http") else f"{API}{path}"
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body else {}),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000]}
        return exc.code, parsed


def upload(tok: str, project_id: str, pdf: Path, document_type: str) -> dict:
    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        f"{API}/api/v1/tenders/{project_id}/documents",
        "-H",
        f"Authorization: Bearer {tok}",
        "-H",
        "Accept: application/json",
        "-F",
        f"file=@{pdf};type=application/pdf",
        "-F",
        f"documentType={document_type}",
        "-F",
        f"logicalName={pdf.name}",
        "-F",
        "includedInAnalysis=true",
    ]
    return json.loads(subprocess.check_output(cmd, text=True))


def poll_doc(tok: str, doc_id: str, max_wait: int = 1800) -> dict:
    start = time.time()
    last = {}
    while time.time() - start < max_wait:
        code, last = api("GET", f"/api/v1/documents/{doc_id}", tok)
        status = last.get("status") or (last.get("currentVersion") or {}).get("processingStatus")
        log(f"[doc] status={status} http={code} t={int(time.time()-start)}s")
        if status in DOC_READY:
            return last
        if status in DOC_FAIL:
            raise SystemExit(f"document failed: {status} {last.get('errorMessage')}")
        time.sleep(10)
    raise SystemExit(f"document not READY after {max_wait}s: {last}")


def poll_job(tok: str, path: str, label: str, max_wait: int = 2400) -> tuple[dict, str]:
    start = time.time()
    last: dict = {}
    while time.time() - start < max_wait:
        try:
            code, last = api("GET", path, tok)
        except Exception as exc:  # noqa: BLE001
            log(f"[{label}] poll_err={exc}")
            tok = token()
            time.sleep(10)
            continue
        status = last.get("status") or last.get("statusConceptCode")
        extra = (
            last.get("errorCode")
            or last.get("error_code")
            or last.get("extractedRequirementCount")
            or last.get("extractedEntityCount")
            or last.get("processed_requirement_count")
            or last.get("errorMessage")
        )
        log(f"[{label}] http={code} status={status} extra={extra} t={int(time.time()-start)}s")
        if status in TERMINAL or status in {"REPORT_JOB_COMPLETED", "REPORT_JOB_FAILED"}:
            return last, tok
        time.sleep(15)
    raise SystemExit(f"{label} timeout: {last}")


def page_items(payload) -> list:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("content", "items", "elements", "data"):
            if isinstance(payload.get(key), list):
                return payload[key]
    return []


def main() -> int:
    if not PDF_PATH.is_file():
        raise SystemExit(f"PDF missing: {PDF_PATH}")

    tok = token()
    step("1_login", email="admin@nanobase.local", via="auto-login", tokenPrefix=tok[:16])

    deadline = (date.today() + timedelta(days=45)).isoformat()
    project_body = {
        "name": f"E2E DSİ Sulama Otomasyon Şartname {time.strftime('%Y%m%d-%H%M%S')}",
        "institutionName": "Devlet Su İşleri Genel Müdürlüğü",
        "tenderRegistrationNumber": f"E2E-DSI-{int(time.time())}",
        "tenderType": "MAL_ALIMI",
        "businessType": "BILISIM",
        "sector": "KAMU_TEKNOLOJI",
        "priority": "HIGH",
        "bidDeadline": deadline,
        "clarificationDeadline": deadline,
        "description": (
            "Kamu açık kaynak: DSİ Sulama Şebekesinde Otomasyon Genel Teknik Şartnamesi R00 "
            "(tarimorman.gov.tr CDN)."
        ),
        "currency": "TRY",
    }
    code, project = api("POST", "/api/v1/tenders", tok, project_body)
    if code not in (200, 201):
        raise SystemExit(f"create project failed: {code} {project}")
    project_id = project.get("id") or project.get("projectId")
    step("2_create_project", http=code, projectId=project_id, projectName=project_body["name"])

    doc = upload(tok, project_id, PDF_PATH, "TECHNICAL_SPECIFICATION")
    doc_id = doc.get("id") or doc.get("documentId")
    step(
        "3_upload_document",
        documentId=doc_id,
        file=PDF_PATH.name,
        bytes=PDF_PATH.stat().st_size,
        sourceUrl=(
            "https://cdniys.tarimorman.gov.tr/api/File/GetGaleriFile/425/DosyaGaleri/622/"
            "sulama_sebekesinde_otomasyon_genel_teknik_sartnamesi_r00_20250520.pdf"
        ),
    )

    # MinIO presence via download-url (presigned)
    dcode, dl = api("GET", f"/api/v1/documents/{doc_id}/download-url", tok)
    step("4_minio_presign", http=dcode, hasUrl=bool(dl.get("url")), expires=dl.get("expiresInSeconds"))

    ready = poll_doc(tok, doc_id)
    step(
        "5_parser_ocr_ready",
        status=ready.get("status"),
        processingStatus=(ready.get("currentVersion") or {}).get("processingStatus"),
        ocrRequired=(ready.get("currentVersion") or {}).get("ocrRequired"),
        ocrQualityScore=(ready.get("currentVersion") or {}).get("ocrQualityScore"),
    )

    ccode, clauses = api("GET", f"/api/v1/documents/{doc_id}/clauses?page=0&size=20", tok)
    clause_items = page_items(clauses)
    step("6_clauses", http=ccode, count=len(clause_items), total=clauses.get("totalElements"))

    rcode, req_job = api("POST", f"/api/v1/documents/{doc_id}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        raise SystemExit(f"requirement extraction start failed: {rcode} {req_job}")
    req_job_id = req_job.get("id") or req_job.get("jobId")
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    step(
        "7_requirement_extraction",
        jobId=req_job_id,
        status=req_done.get("status"),
        extracted=req_done.get("extractedRequirementCount"),
    )

    qcode, reqs = api(
        "GET",
        f"/api/v1/tenders/{project_id}/requirements?size=50&sort=createdAt,desc",
        tok,
    )
    req_items = page_items(reqs)
    step("8_list_requirements", http=qcode, count=len(req_items), total=reqs.get("totalElements"))
    if not req_items:
        REPORT["ok"] = False
        step("ABORT", reason="no requirements extracted — compliance cannot run")
        Path("/tmp/full_product_e2e_report.json").write_text(
            json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        return 2

    # Knowledge extraction on same TIM-like doc (company capabilities may already exist)
    kcode, know_job = api("POST", f"/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
    if kcode in (200, 201, 202):
        know_id = know_job.get("id") or know_job.get("jobId")
        know_done, tok = poll_job(tok, f"/api/v1/knowledge-extractions/{know_id}", "knowledge")
        step(
            "9a_knowledge_extraction",
            jobId=know_id,
            status=know_done.get("status"),
            entities=know_done.get("extractedEntityCount"),
        )
    else:
        step("9a_knowledge_extraction", skipped=True, http=kcode, detail=know_job)

    ecode, evidence = api("GET", "/api/v1/evidence?page=0&size=20", tok)
    ccode2, caps = api("GET", "/api/v1/company-capabilities?page=0&size=20", tok)
    kcode2, entities = api("GET", "/api/v1/knowledge/entities?page=0&size=20", tok)
    step(
        "9_evidence_match",
        evidenceHttp=ecode,
        evidenceCount=len(page_items(evidence)) or evidence.get("totalElements"),
        capabilitiesHttp=ccode2,
        capabilityCount=len(page_items(caps)) or caps.get("totalElements"),
        entitiesHttp=kcode2,
        entityCount=len(page_items(entities)) if page_items(entities) else (
            len(entities) if isinstance(entities, list) else entities.get("totalElements")
        ),
    )

    jcode, job = api("POST", f"/api/v1/tenders/{project_id}/compliance-analyses", tok)
    if jcode not in (200, 201, 202):
        raise SystemExit(f"compliance start failed: {jcode} {job}")
    job_id = job.get("id") or job.get("jobId")
    job_done, tok = poll_job(tok, f"/api/v1/compliance-analyses/{job_id}", "compliance", max_wait=3600)
    step(
        "10_compliance_job",
        jobId=job_id,
        status=job_done.get("status"),
        processed=job_done.get("processed_requirement_count") or job_done.get("processedRequirementCount"),
        completed=job_done.get("completed_count") or job_done.get("completedCount"),
        failed=job_done.get("failed_count") or job_done.get("failedCount"),
    )

    ecode2, evals = api("GET", f"/api/v1/tenders/{project_id}/compliance-evaluations", tok)
    eval_items = page_items(evals)
    sample = eval_items[0] if eval_items else {}
    step(
        "11_results_evidence",
        http=ecode2,
        evaluationCount=len(eval_items) or evals.get("totalElements"),
        sampleId=sample.get("id"),
        suggested=sample.get("suggestedDecision") or sample.get("suggestedDecisionCode"),
        evidenceCount=sample.get("evidenceCount"),
        grounding=sample.get("groundingStatus") or sample.get("groundingStatusCode"),
    )

    # Risk analysis
    risk_status = None
    ricode, risk_job = api("POST", f"/api/v1/tenders/{project_id}/risk-analyses", tok)
    if ricode in (200, 201, 202):
        risk_id = risk_job.get("id") or risk_job.get("jobId")
        risk_done, tok = poll_job(tok, f"/api/v1/risk-analyses/{risk_id}", "risk", max_wait=1800)
        risk_status = risk_done.get("status")
        step("12a_risk_job", jobId=risk_id, status=risk_status)
    else:
        step("12a_risk_job", skipped=True, http=ricode, detail=str(risk_job)[:400])

    rscode, risks = api("GET", f"/api/v1/tenders/{project_id}/risks", tok)
    cfcode, conflicts = api("GET", f"/api/v1/tenders/{project_id}/conflicts", tok)
    step(
        "12_risks_conflicts",
        risksHttp=rscode,
        riskCount=len(page_items(risks)) or risks.get("totalElements"),
        conflictsHttp=cfcode,
        conflictCount=len(page_items(conflicts)) or conflicts.get("totalElements"),
    )

    # Human review / correction on first evaluation
    reviewed = None
    if sample.get("id"):
        decision = COMPLIANT if sample.get("evidenceCount") else PARTIALLY
        rvcode, reviewed = api(
            "POST",
            f"/api/v1/compliance-evaluations/{sample['id']}/review",
            tok,
            {
                "finalDecisionConceptId": decision,
                "changeTypeConceptId": EVALUATION_REVIEWED,
                "feedbackTypeConceptId": None,
                "reason": "E2E canlı doğrulama: kullanıcı onay/düzeltme adımı",
            },
        )
        step("13_human_review", http=rvcode, evaluationId=sample["id"], body=reviewed if rvcode < 300 else str(reviewed)[:400])
    else:
        step("13_human_review", skipped=True, reason="no evaluations")

    # Report definition + generate + download
    stamp = int(time.time())
    dcode2, definition = api(
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
    vcode, version = api(
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
        f"/api/v1/tenders/{project_id}/reports",
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
        "14_report_generate",
        defHttp=dcode2,
        versionHttp=vcode,
        generateHttp=gcode,
        status=report_job.get("statusConceptCode") or report_job.get("status"),
        artifactCount=len(artifacts),
        jobId=report_job.get("id"),
    )

    download_path = None
    if artifacts:
        art_id = artifacts[0].get("id")
        ucode, url_body = api("GET", f"/api/v1/report-artifacts/{art_id}/download-url", tok)
        url = url_body.get("url")
        if url:
            download_path = f"/tmp/e2e_report_{project_id}.pdf"
            subprocess.check_call(["curl", "-fsSL", "-o", download_path, url])
            size = Path(download_path).stat().st_size
            step("15_report_download", http=ucode, path=download_path, bytes=size)
        else:
            step("15_report_download", http=ucode, error=url_body)
            REPORT["ok"] = False
    else:
        step("15_report_download", skipped=True, detail=str(report_job)[:500])
        REPORT["ok"] = False

    acode, audit = api("GET", "/api/v1/audit-events?page=0&size=30&sort=createdAt,desc", tok)
    audit_items = page_items(audit)
    interesting = [
        {
            "eventType": a.get("eventType"),
            "entityType": a.get("entityType"),
            "entityId": a.get("entityId"),
            "createdAt": a.get("createdAt"),
        }
        for a in audit_items[:15]
    ]
    step("16_audit_history", http=acode, count=len(audit_items) or audit.get("totalElements"), sample=interesting)

    REPORT["projectId"] = project_id
    REPORT["documentId"] = doc_id
    REPORT["complianceJobId"] = job_id
    REPORT["portal"] = {
        "project": f"https://portal.nanobase.ai/legal/#/project/{project_id}/analysis/documents",
        "requirements": f"https://portal.nanobase.ai/legal/#/project/{project_id}/analysis/requirements",
        "compliance": f"https://portal.nanobase.ai/legal/#/project/{project_id}/analysis/compliance",
        "activity": f"https://portal.nanobase.ai/legal/#/project/{project_id}/project/activity",
    }
    out = Path("/tmp/full_product_e2e_report.json")
    out.write_text(json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8")
    log(f"REPORT_WRITTEN {out} ok={REPORT['ok']}")
    return 0 if REPORT["ok"] and job_done.get("status") == "COMPLETED" else 1


if __name__ == "__main__":
    sys.exit(main())
