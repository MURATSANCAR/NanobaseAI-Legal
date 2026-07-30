#!/usr/bin/env python3
"""E2E-01 autonomous DSİ gate — zero manual SQL seed.

Produces /tmp/nanobase-e2e/full_product_e2e_autonomous_report.json with all required IDs.
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import urlparse

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
PDF_PATH = Path(
    os.environ.get(
        "E2E_PDF",
        "/tmp/nanobase-e2e/DSI_Sulama_Otomasyon_Genel_Teknik_Sartname.pdf",
    )
)
ART_DIR = Path(os.environ.get("E2E_ART_DIR", "/tmp/nanobase-e2e"))
OUT = Path(os.environ.get("E2E_REPORT", str(ART_DIR / "full_product_e2e_autonomous_report.json")))
METRICS_OUT = ART_DIR / "full_product_e2e_metrics.json"
SRC_DL = ART_DIR / "full_product_e2e_downloaded_source.pdf"
REP_DL = ART_DIR / "full_product_e2e_autonomous_report.pdf"

TERMINAL = {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}
DOC_READY = {"READY"}
DOC_FAIL = {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}

CUSTOM_QUERY_SECTION = "70000000-0000-0000-0000-000000000041"
PDF_FORMAT = "70000000-0000-0000-0000-000000000038"
DATA_POLICY = "70000000-0000-0000-0000-000000000101"
TEMPLATE = "70000000-0000-0000-0000-000000000111"
EVALUATION_REVIEWED = "50000000-0000-0000-0000-00000000001c"
COMPLIANT = "50000000-0000-0000-0000-00000000000b"
PARTIALLY = "50000000-0000-0000-0000-00000000000c"

T0 = time.time()
TIMINGS: dict[str, float] = {}
REPORT: dict = {
    "gate": "E2E-01-AUTONOMOUS-DSI",
    "ok": True,
    "blockers": [],
    "manualClauseSeed": 0,
    "manualRequirementSeed": 0,
    "forbiddenActions": ["SQL_SEED_CLAUSES", "SQL_SEED_REQUIREMENTS", "FIXTURE_PROJECT_REUSE"],
    "steps": [],
}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(name: str, **payload) -> None:
    entry = {"step": name, "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), **payload}
    REPORT["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:700]}")


def fail(reason: str, classification: str | None = None) -> None:
    REPORT["ok"] = False
    entry = reason if classification is None else f"{classification}:{reason}"
    REPORT["blockers"].append(entry)
    step("BLOCKER", reason=reason, classification=classification)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def token() -> str:
    body = json.loads(
        subprocess.check_output(
            [
                "curl", "-sS", "-X", "POST", f"{API}/api/v1/auth/auto-login",
                "-H", "Accept: application/json",
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
        try:
            raw = exc.read().decode(errors="replace")
        except Exception:  # noqa: BLE001
            raw = ""
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000]}
        return exc.code, parsed
    except Exception as exc:  # noqa: BLE001
        return 599, {"error": str(exc)}


def upload(tok: str, project_id: str, pdf: Path) -> dict:
    cmd = [
        "curl", "-sS", "-X", "POST",
        f"{API}/api/v1/tenders/{project_id}/documents",
        "-H", f"Authorization: Bearer {tok}",
        "-H", "Accept: application/json",
        "-F", f"file=@{pdf};type=application/pdf",
        "-F", "documentType=TECHNICAL_SPECIFICATION",
        "-F", f"logicalName={pdf.name}",
        "-F", "includedInAnalysis=true",
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
            TIMINGS["parser_ocr_s"] = time.time() - start
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
        log(f"[{label}] http={code} status={status} t={int(time.time()-start)}s")
        if status in TERMINAL or status in {"REPORT_JOB_COMPLETED", "REPORT_JOB_FAILED"}:
            TIMINGS[f"{label}_s"] = time.time() - start
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


def proxy_download(tok: str, url_path: str, dest: Path) -> tuple[int, int]:
    dest.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [
            "curl", "-sS", "-o", str(dest), "-w", "%{http_code}",
            "-H", f"Authorization: Bearer {tok}",
            f"{API}{url_path}",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    http = int(result.stdout.strip() or "0")
    size = dest.stat().st_size if http == 200 and dest.is_file() else 0
    return http, size


def sql(query: str) -> str:
    """Read-only SQL via postgres container (server-side)."""
    try:
        return subprocess.check_output(
            [
                "sudo", "docker", "exec", "-i", "actenora-prodlike-postgres",
                "psql", "-U", "actenora", "-d", "specai", "-At", "-c", query,
            ],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except Exception as exc:  # noqa: BLE001
        return f"SQL_ERROR:{exc}"


def main() -> int:
    ART_DIR.mkdir(parents=True, exist_ok=True)
    if not PDF_PATH.is_file():
        raise SystemExit(f"PDF missing: {PDF_PATH}")

    pdf_sha = sha256_file(PDF_PATH)
    REPORT["dsiPdfSha256"] = pdf_sha
    REPORT["dsiPdfBytes"] = PDF_PATH.stat().st_size

    tok = token()
    step("1_login", via="auto-login")

    deadline = (date.today() + timedelta(days=45)).isoformat()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    t_upload = time.time()
    code, project = api(
        "POST", "/api/v1/tenders", tok,
        {
            "name": f"AUTON E2E-01 DSİ {stamp}",
            "institutionName": "Devlet Su İşleri Genel Müdürlüğü",
            "tenderRegistrationNumber": f"AUTON-DSI-{int(time.time())}",
            "tenderType": "MAL_ALIMI",
            "businessType": "BILISIM",
            "sector": "KAMU_TEKNOLOJI",
            "priority": "HIGH",
            "bidDeadline": deadline,
            "clarificationDeadline": deadline,
            "description": "Autonomous DI gate — no SQL seed",
            "currency": "TRY",
        },
    )
    if code not in (200, 201):
        raise SystemExit(f"create project failed: {code} {project}")
    project_id = project.get("id") or project.get("projectId")
    step("2_create_project", projectId=project_id)

    doc = upload(tok, project_id, PDF_PATH)
    TIMINGS["upload_s"] = time.time() - t_upload
    doc_id = doc.get("id") or doc.get("documentId")
    version = doc.get("currentVersion") or {}
    version_id = version.get("id") or doc.get("documentVersionId") or version.get("documentVersionId")
    parser_job_id = (
        doc.get("processingJobId")
        or (doc.get("latestJob") or {}).get("id")
        or version.get("processingJobId")
    )
    step("3_upload", documentId=doc_id, versionId=version_id, bytes=PDF_PATH.stat().st_size)

    dcode, dl = api("GET", f"/api/v1/documents/{doc_id}/download-url", tok)
    if dcode >= 400:
        # Retry once after short delay (backend may still be warming MinIO client).
        time.sleep(3)
        dcode, dl = api("GET", f"/api/v1/documents/{doc_id}/download-url", tok)
    url = dl.get("url") or ""
    host = urlparse(url).hostname if url else None
    step("4_presign", http=dcode, host=host)
    if dcode >= 400:
        step("4_presign_fallback", detail="presign failed; proxy download is authoritative")
    elif host and ("actenora" in host or host == "minio"):
        fail(f"presign host is Docker-internal: {host}", "OBJECT_DOWNLOAD")

    http, size = proxy_download(tok, f"/api/v1/documents/{doc_id}/download", SRC_DL)
    step("4b_proxy_download", http=http, bytes=size)
    doc_dl = "PASS"
    if http != 200 or size < 1000:
        fail("document proxy download failed", "OBJECT_DOWNLOAD")
        doc_dl = "FAIL"
    else:
        dl_sha = sha256_file(SRC_DL)
        if dl_sha != pdf_sha:
            fail(f"download SHA mismatch upload={pdf_sha} dl={dl_sha}", "OBJECT_DOWNLOAD")
            doc_dl = "FAIL"

    # Unauthorized smoke
    bad = subprocess.run(
        ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
         f"{API}/api/v1/documents/{doc_id}/download"],
        check=False, capture_output=True, text=True,
    )
    bad_http = int(bad.stdout.strip() or "0")
    tenant_iso = "PASS" if bad_http in (401, 403, 404) else "FAIL"
    step("4c_tenant_isolation_smoke", unauthDownloadHttp=bad_http, result=tenant_iso)
    if tenant_iso != "PASS":
        fail(f"unauth download expected 401/403/404 got {bad_http}", "TENANT_AUTHORIZATION")

    ready = poll_doc(tok, doc_id)
    if not version_id:
        version_id = (ready.get("currentVersion") or {}).get("id")
    if not parser_job_id:
        # resolve from jobs API if present
        jcode, jobs = api("GET", f"/api/v1/documents/{doc_id}/processing-jobs", tok)
        items = page_items(jobs)
        if items:
            parser_job_id = items[0].get("id")
        step("5_parser_jobs", http=jcode, jobId=parser_job_id)
    step(
        "5_parser_ready",
        status=ready.get("status"),
        versionId=version_id,
        pageCount=(ready.get("currentVersion") or {}).get("pageCount"),
        ocr=(ready.get("currentVersion") or {}).get("ocrRequired"),
    )

    # Layout / clause SQL counts (read-only)
    layout_count = 0
    recurring_count = 0
    if version_id:
        layout_count = int(sql(
            f"select count(*) from document_layout_block where document_version_id='{version_id}'"
        ) or "0")
        recurring_count = int(sql(
            f"select count(*) from recurring_page_element where document_version_id='{version_id}'"
        ) or "0")

    ccode, clauses = api("GET", f"/api/v1/documents/{doc_id}/clauses?page=0&size=100", tok)
    clause_items = page_items(clauses)
    clause_total = int(clauses.get("totalElements") or len(clause_items) or 0)
    lengths = [len((c.get("rawText") or c.get("normalizedText") or "")) for c in clause_items]
    avg_len = (sum(lengths) / len(lengths)) if lengths else 0
    max_len = max(lengths) if lengths else 0
    step(
        "6_clauses",
        http=ccode,
        total=clause_total,
        layoutBlocks=layout_count,
        recurring=recurring_count,
        avgChars=round(avg_len),
        maxChars=max_len,
    )
    if clause_total <= 0:
        fail("clauses=0", "CLAUSE_SEGMENTATION")
    if max_len > 8000:
        fail(f"unbounded page-size clause maxChars={max_len}", "CLAUSE_CHUNKING")

    rcode, req_job = api("POST", f"/api/v1/documents/{doc_id}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        raise SystemExit(f"requirement start failed: {rcode} {req_job}")
    req_job_id = req_job.get("id") or req_job.get("jobId")
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    extracted = int(req_done.get("extractedRequirementCount") or 0)
    empty_code = (
        req_done.get("emptyOutcomeCode")
        or req_done.get("empty_outcome_code")
    )
    suspicious = int(
        req_done.get("suspiciousEmptyCount")
        or req_done.get("suspicious_empty_count")
        or 0
    )
    # SQL counters fallback
    if version_id and not suspicious:
        row = sql(
            "select coalesce(suspicious_empty_count,0)||'|'||coalesce(timeout_empty_count,0)"
            "||'|'||coalesce(schema_failure_count,0)||'|'||coalesce(empty_outcome_code,'')"
            f" from requirement_extraction_job where id='{req_job_id}'"
        )
        if row and not row.startswith("SQL_ERROR"):
            parts = row.split("|")
            if len(parts) >= 4:
                suspicious = int(parts[0] or 0)
                REPORT["timeoutEmpty"] = int(parts[1] or 0)
                REPORT["schemaFailures"] = int(parts[2] or 0)
                empty_code = empty_code or parts[3] or None

    step(
        "7_requirements",
        jobId=req_job_id,
        status=req_done.get("status"),
        extracted=extracted,
        emptyOutcome=empty_code,
        suspiciousEmpty=suspicious,
    )
    if extracted <= 0:
        fail("automaticRequirementCount=0", "REQUIREMENT_EXTRACTION")
    if req_done.get("status") == "FAILED":
        fail("requirement job FAILED", "REQUIREMENT_EXTRACTION")
    if suspicious > 0 and extracted == 0:
        fail("unresolved SUSPICIOUS_EMPTY", "REQUIREMENT_SIGNAL")

    # Sample grounding
    qcode, reqs = api(
        "GET", f"/api/v1/tenders/{project_id}/requirements?size=10&sort=createdAt,desc", tok
    )
    req_items = page_items(reqs)
    grounded = 0
    for item in req_items[:5]:
        if item.get("sourceClauseId") or item.get("documentId"):
            grounded += 1
    step("7b_grounding_sample", http=qcode, sample=len(req_items[:5]), grounded=grounded)
    if req_items and grounded == 0:
        fail("requirement grounding missing on sample", "REQUIREMENT_GROUNDING")

    knowledge_id = None
    knowledge_status = None
    knowledge_terminal = None
    kcode, know_job = api("POST", f"/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
    if kcode in (200, 201, 202):
        knowledge_id = know_job.get("id") or know_job.get("jobId")
        know_done, tok = poll_job(tok, f"/api/v1/knowledge-extractions/{knowledge_id}", "knowledge")
        knowledge_status = know_done.get("status")
        stage = know_done.get("current_stage_code") or know_done.get("currentStageCode")
        purpose = know_done.get("document_purpose_code") or know_done.get("documentPurposeCode")
        existing = know_done.get("existing_knowledge_used") or know_done.get("existingKnowledgeUsed")
        if stage == "SKIPPED_NOT_APPLICABLE" or (
            knowledge_status == "COMPLETED" and (know_done.get("extracted_entity_count") in (0, None, 0))
            and purpose in ("TENDER_SPEC", None)
        ):
            # Confirm via SQL stage
            stage_sql = sql(
                "select stage_code from knowledge_extraction_run_stage"
                f" where knowledge_job_id='{knowledge_id}' order by created_at desc limit 5"
            )
            if "SKIPPED_NOT_APPLICABLE" in stage_sql or stage == "SKIPPED_NOT_APPLICABLE":
                knowledge_terminal = "SKIPPED_NOT_APPLICABLE"
            else:
                knowledge_terminal = "COMPLETED"
        elif knowledge_status == "COMPLETED":
            knowledge_terminal = "COMPLETED"
        else:
            knowledge_terminal = knowledge_status
        step(
            "8_knowledge",
            jobId=knowledge_id,
            status=knowledge_status,
            terminal=knowledge_terminal,
            purpose=purpose,
            stage=stage,
            existingUsed=existing,
            entities=know_done.get("extracted_entity_count") or know_done.get("extractedEntityCount"),
            error=know_done.get("error_code") or know_done.get("errorCode"),
        )
        if knowledge_terminal not in {"COMPLETED", "SKIPPED_NOT_APPLICABLE"}:
            fail(f"knowledge terminal invalid: {knowledge_terminal}", "KNOWLEDGE_EXTRACTION")
    else:
        fail(f"knowledge start failed http={kcode}", "KNOWLEDGE_EXTRACTION")

    jcode, job = api("POST", f"/api/v1/tenders/{project_id}/compliance-analyses", tok)
    if jcode not in (200, 201, 202):
        raise SystemExit(f"compliance start failed: {jcode} {job}")
    compliance_id = job.get("id") or job.get("jobId")
    job_done, tok = poll_job(
        tok, f"/api/v1/compliance-analyses/{compliance_id}", "compliance", max_wait=7200
    )
    step("9_compliance", jobId=compliance_id, status=job_done.get("status"),
         processed=job_done.get("processedRequirementCount") or job_done.get("processed_requirement_count"),
         completed=job_done.get("completedCount") or job_done.get("completed_count"),
         failed=job_done.get("failedCount") or job_done.get("failed_count"))
    if job_done.get("status") != "COMPLETED":
        fail(f"compliance not COMPLETED: {job_done.get('status')}", "COMPLIANCE")

    ecode2, evals = api("GET", f"/api/v1/tenders/{project_id}/compliance-evaluations", tok)
    eval_items = page_items(evals)
    evaluation_count = int(evals.get("totalElements") or len(eval_items) or 0)
    sample = eval_items[0] if eval_items else {}
    review_id = None
    if sample.get("id"):
        decision = COMPLIANT if sample.get("evidenceCount") else PARTIALLY
        rv, reviewed = api(
            "POST",
            f"/api/v1/compliance-evaluations/{sample['id']}/review",
            tok,
            {
                "finalDecisionConceptId": decision,
                "changeTypeConceptId": EVALUATION_REVIEWED,
                "feedbackTypeConceptId": None,
                "reason": "E2E-01 autonomous review",
            },
        )
        review_id = sample["id"]
        step("9b_human_review", http=rv, evaluationId=review_id)
        if rv >= 300:
            fail(f"review failed http={rv}", "COMPLIANCE")

    risk_id = None
    risk_count = 0
    conflict_count = 0
    ricode, risk_job = api("POST", f"/api/v1/tenders/{project_id}/risk-analyses", tok)
    if ricode in (200, 201, 202):
        risk_id = risk_job.get("id") or risk_job.get("jobId")
        risk_done, tok = poll_job(tok, f"/api/v1/risk-analyses/{risk_id}", "risk", max_wait=1800)
        step("10a_risk", jobId=risk_id, status=risk_done.get("status"))
        if risk_done.get("status") not in TERMINAL:
            fail("risk not terminal", "RISK")
    else:
        step("10a_risk", skipped=True, http=ricode)

    rscode, risks = api("GET", f"/api/v1/tenders/{project_id}/risks", tok)
    cfcode, conflicts = api("GET", f"/api/v1/tenders/{project_id}/conflicts", tok)
    risk_count = int(risks.get("totalElements") or len(page_items(risks)) or 0)
    conflict_count = int(conflicts.get("totalElements") or len(page_items(conflicts)) or 0)
    step("10_risks_conflicts", riskCount=risk_count, conflictCount=conflict_count,
         risksHttp=rscode, conflictsHttp=cfcode)

    stamp_i = int(time.time())
    _, definition = api(
        "POST", "/api/v1/report-definitions", tok,
        {
            "reportCode": f"AUTON_E2E_{stamp_i}",
            "name": "Autonomous E2E Report",
            "description": "Integrity gate",
            "scope": "ORGANIZATION",
        },
    )
    def_id = definition.get("id")
    _, version_def = api(
        "POST", f"/api/v1/report-definitions/{def_id}/versions", tok,
        {
            "sectionConfiguration": {},
            "dataPolicyVersionId": DATA_POLICY,
            "templateVersionId": TEMPLATE,
            "sections": [
                {
                    "sectionCode": "DYNAMIC_SUMMARY",
                    "sectionTypeConceptId": CUSTOM_QUERY_SECTION,
                    "titleTemplate": "Project summary requirements compliance risks",
                    "dataQueryConfiguration": {"snapshotJsonPointer": "/counts"},
                    "renderConfiguration": {},
                    "visibilityCondition": {},
                    "sortOrder": 1,
                }
            ],
        },
    )
    version_def_id = version_def.get("id")
    api("POST", f"/api/v1/report-definition-versions/{version_def_id}/activate", tok)
    gcode, report_job = api(
        "POST",
        f"/api/v1/tenders/{project_id}/reports",
        tok,
        {
            "reportDefinitionVersionId": version_def_id,
            "formatConceptIds": [PDF_FORMAT],
            "staleOverrideApprovalId": None,
        },
        timeout=300,
    )
    report_status = report_job.get("statusConceptCode") or report_job.get("status")
    artifacts = report_job.get("artifacts") or []
    report_job_id = report_job.get("id")
    report_artifact_id = artifacts[0].get("id") if artifacts else None
    report_integrity = "FAIL"
    report_dl = "FAIL"
    report_bytes = 0
    step("11_report", http=gcode, status=report_status, artifacts=len(artifacts), jobId=report_job_id)
    if report_status not in {"REPORT_JOB_COMPLETED", "COMPLETED"}:
        fail(f"report status={report_status} err={report_job.get('errorCode')}", "REPORT_INTEGRITY")
    elif not artifacts:
        fail("report has no artifacts", "REPORT_RENDER")
    else:
        report_bytes = int(artifacts[0].get("fileSize") or 0)
        if report_bytes < 1200:
            fail(f"report too small: {report_bytes}", "REPORT_INTEGRITY")
        else:
            report_integrity = "PASS"
        rhttp, rsize = proxy_download(
            tok, f"/api/v1/report-artifacts/{report_artifact_id}/download", REP_DL
        )
        step("11b_report_proxy", http=rhttp, bytes=rsize)
        if rhttp == 200 and rsize >= 1200 and REP_DL.read_bytes()[:4] == b"%PDF":
            report_dl = "PASS"
        else:
            fail("report proxy download failed", "OBJECT_DOWNLOAD")

    # Unauth report
    bad_r = subprocess.run(
        ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
         f"{API}/api/v1/report-artifacts/{report_artifact_id}/download"],
        check=False, capture_output=True, text=True,
    ) if report_artifact_id else None
    if bad_r is not None:
        rh = int(bad_r.stdout.strip() or "0")
        if rh not in (401, 403, 404):
            fail(f"unauth report download http={rh}", "TENANT_AUTHORIZATION")
            tenant_iso = "FAIL"
        step("11c_tenant_report", http=rh)

    acode, audit = api("GET", "/api/v1/audit-events?page=0&size=50&sort=createdAt,desc", tok)
    audit_items = page_items(audit)
    audit_count = int(audit.get("totalElements") or len(audit_items) or 0)
    audit_result = "PASS" if audit_count > 0 and acode < 300 else "FAIL"
    if audit_result != "PASS":
        fail("audit empty", "AUDIT")
    step("12_audit", http=acode, count=audit_count)

    # DB consistency (read-only)
    consistency = {
        "orphanClauses": sql(
            "select count(*) from clause c left join document_version v on v.id=c.document_version_id"
            " where v.id is null"
        ),
        "reqWithoutSource": sql(
            f"select count(*) from requirement where project_id='{project_id}'"
            " and source_clause_id is null"
        ) if project_id else "n/a",
        "stuckCompliance": sql(
            f"select count(*) from compliance_analysis_job where id='{compliance_id}'"
            " and status in ('QUEUED','RUNNING')"
        ) if compliance_id else "n/a",
    }
    step("13_db_consistency", **consistency)

    TIMINGS["full_e2e_s"] = time.time() - T0
    METRICS_OUT.write_text(json.dumps(TIMINGS, indent=2), encoding="utf-8")

    REPORT.update(
        {
            "projectId": project_id,
            "documentId": doc_id,
            "documentVersionId": version_id,
            "parserJobId": parser_job_id,
            "requirementJobId": req_job_id,
            "knowledgeJobId": knowledge_id,
            "knowledgeTerminalStatus": knowledge_terminal,
            "complianceJobId": compliance_id,
            "riskAnalysisJobId": risk_id,
            "reportJobId": report_job_id,
            "reportArtifactId": report_artifact_id,
            "reviewId": review_id,
            "clauseCount": clause_total,
            "layoutBlockCount": layout_count,
            "recurringElementCount": recurring_count,
            "automaticRequirementCount": extracted,
            "evaluationCount": evaluation_count,
            "riskCount": risk_count,
            "conflictCount": conflict_count,
            "reportIntegrity": report_integrity,
            "reportBytes": report_bytes,
            "documentDownload": doc_dl,
            "reportDownload": report_dl,
            "tenantIsolationSmoke": tenant_iso,
            "audit": audit_result,
            "auditEventCount": audit_count,
            "suspiciousEmpty": suspicious,
            "emptyOutcomeCode": empty_code,
            "timings": TIMINGS,
            "consistency": consistency,
        }
    )
    OUT.write_text(json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8")
    log(f"REPORT_WRITTEN {OUT} ok={REPORT['ok']} blockers={REPORT['blockers']}")
    return 0 if REPORT["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
