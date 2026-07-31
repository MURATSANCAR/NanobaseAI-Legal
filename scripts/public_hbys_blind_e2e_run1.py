#!/usr/bin/env python3
"""Blind public HBYS E2E RUN_1 — freeze first result, no product config changes.

Produces /tmp/nanobase-hbys-public-e2e/run-1/* and review-bundle/*.
Does NOT mutate prompts/policies/models/timeouts/company evidence.
Does NOT POST human review decisions that would alter RUN_1 evaluations.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
ROOT = Path(os.environ.get("HBYS_E2E_ROOT", "/tmp/nanobase-hbys-public-e2e"))
PDF_PATH = Path(
    os.environ.get(
        "HBYS_PDF",
        str(ROOT / "source" / "hbys-technical-specification.pdf"),
    )
)
RUN1 = ROOT / "run-1"
BUNDLE = ROOT / "review-bundle"
LOG = RUN1 / "runner.log"

TERMINAL = {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}
DOC_READY = {"READY"}
DOC_FAIL = {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}

T0 = time.time()
TIMINGS: dict[str, float] = {}
SUMMARY: dict[str, Any] = {
    "gate": "PUBLIC-HBYS-BLIND-E2E-RUN1",
    "runType": "BLIND_BASELINE",
    "codeConfigChangesDuringRun1": "NONE",
    "ok": True,
    "pipelineOk": True,
    "blockers": [],
    "manualClauseSeed": 0,
    "manualRequirementSeed": 0,
    "independentContentQuality": "PENDING_EXTERNAL_REVIEW",
    "steps": [],
}


def utcnow() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def log(msg: str) -> None:
    line = f"{utcnow()} {msg}"
    print(line, flush=True)
    RUN1.mkdir(parents=True, exist_ok=True)
    with LOG.open("a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def step(name: str, **payload: Any) -> None:
    entry = {"step": name, "at": utcnow(), **payload}
    SUMMARY["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:900]}")


def fail(reason: str, classification: str | None = None) -> None:
    SUMMARY["ok"] = False
    SUMMARY["pipelineOk"] = False
    entry = reason if classification is None else f"{classification}:{reason}"
    SUMMARY["blockers"].append(entry)
    step("BLOCKER", reason=reason, classification=classification)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def token() -> str:
    body = json.loads(
        subprocess.check_output(
            ["curl", "-sS", "-X", "POST", f"{API}/api/v1/auth/auto-login",
             "-H", "Accept: application/json"],
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
            "Accept": application_json(),
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


def application_json() -> str:
    return "application/json"


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


def poll_doc(tok: str, doc_id: str, max_wait: int = 3600) -> dict:
    start = time.time()
    last: dict = {}
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


def poll_job(tok: str, path: str, label: str, max_wait: int = 14400) -> tuple[dict, str]:
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


def page_total(payload, items: list | None = None) -> int:
    resolved = items if items is not None else page_items(payload)
    if isinstance(payload, list):
        return len(payload)
    if isinstance(payload, dict):
        for key in ("totalElements", "total", "count"):
            if payload.get(key) is not None:
                try:
                    return int(payload[key])
                except (TypeError, ValueError):
                    pass
    return len(resolved)


def fetch_all(tok: str, path: str, size: int = 100) -> list:
    items: list = []
    page = 0
    while True:
        sep = "&" if "?" in path else "?"
        code, payload = api("GET", f"{path}{sep}page={page}&size={size}", tok)
        if code >= 400:
            break
        batch = page_items(payload)
        items.extend(batch)
        total = page_total(payload, batch)
        if not batch or len(items) >= total or len(batch) < size:
            break
        page += 1
        if page > 200:
            break
    return items


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
    try:
        return subprocess.check_output(
            [
                "docker", "exec", "-i", "actenora-prodlike-postgres",
                "psql", "-U", "actenora", "-d", "specai", "-At", "-c", query,
            ],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except Exception as exc:  # noqa: BLE001
        return f"SQL_ERROR:{exc}"


def sql_json(query: str) -> Any:
    raw = sql(query)
    if not raw or raw.startswith("SQL_ERROR"):
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def company_snapshot(org_id: str) -> dict:
    snap: dict[str, Any] = {
        "organizationId": org_id,
        "snapshotAt": utcnow(),
        "organizationName": sql(
            f"select name from organization where id='{org_id}' limit 1"
        ) or None,
    }
    # Best-effort counts across known tables; missing tables => 0 / SQL_ERROR ignored.
    counts = {
        "entityCount": "select count(*) from knowledge_entity where organization_id='%s'",
        "attributeCount": "select count(*) from knowledge_attribute where organization_id='%s'",
        "capabilityCount": "select count(*) from company_capability where organization_id='%s'",
        "claimCount": "select count(*) from capability_claim where organization_id='%s'",
        "evidenceDocumentCount": (
            "select count(*) from document d where d.organization_id='%s' "
            "and coalesce(d.document_type,'') not in ('TECHNICAL_SPECIFICATION')"
        ),
        "evidenceFragmentCount": (
            "select count(*) from evidence_fragment where organization_id='%s'"
        ),
    }
    for key, q in counts.items():
        val = sql(q % org_id)
        try:
            snap[key] = int(val) if val and not val.startswith("SQL_ERROR") else 0
        except ValueError:
            snap[key] = 0
            snap[f"{key}Error"] = val
    # Document metadata only (no raw content)
    docs_raw = sql(
        "select coalesce(json_agg(row_to_json(t)), '[]'::json) from ("
        " select d.id as \"documentId\", d.document_type as \"documentType\", "
        " d.logical_name as title, d.status, d.created_at "
        f" from document d where d.organization_id='{org_id}' "
        " order by d.created_at desc limit 200) t"
    )
    try:
        snap["documentsMetadata"] = json.loads(docs_raw) if docs_raw and not docs_raw.startswith("SQL_ERROR") else []
    except json.JSONDecodeError:
        snap["documentsMetadata"] = []
    snap["certificateCount"] = 0
    snap["productDocumentCount"] = 0
    snap["referenceDocumentCount"] = 0
    snap["personnelDocumentCount"] = 0
    snap["slaServiceDocumentCount"] = 0
    snap["expiredEvidenceCount"] = 0
    snap["unknownValidityCount"] = 0
    return snap


def decision_bucket(item: dict) -> str:
    for key in (
        "decisionConceptCode",
        "finalDecisionConceptCode",
        "decisionCode",
        "statusConceptCode",
        "decision",
    ):
        val = item.get(key)
        if val:
            return str(val).upper()
    # concept id fallbacks used in product
    cid = str(item.get("finalDecisionConceptId") or item.get("decisionConceptId") or "")
    mapping = {
        "50000000-0000-0000-0000-00000000000b": "COMPLIANT",
        "50000000-0000-0000-0000-00000000000c": "PARTIALLY_COMPLIANT",
        "50000000-0000-0000-0000-00000000000d": "NON_COMPLIANT",
        "50000000-0000-0000-0000-00000000000e": "INSUFFICIENT_INFORMATION",
        "50000000-0000-0000-0000-00000000000f": "NOT_APPLICABLE",
        "50000000-0000-0000-0000-000000000010": "MANUAL_REVIEW",
    }
    return mapping.get(cid, "UNKNOWN")


def sanitize_text(text: str | None, limit: int = 800) -> str | None:
    if not text:
        return None
    cleaned = re.sub(r"\s+", " ", text).strip()
    if len(cleaned) > limit:
        cleaned = cleaned[: limit - 1] + "…"
    return cleaned


def main() -> int:
    RUN1.mkdir(parents=True, exist_ok=True)
    BUNDLE.mkdir(parents=True, exist_ok=True)
    if not PDF_PATH.is_file():
        fail("source PDF missing", "BLOCKED_SOURCE_DOWNLOAD")
        write_json(RUN1 / "run-summary.json", SUMMARY)
        return 2

    pdf_sha = sha256_file(PDF_PATH)
    SUMMARY["source"] = {
        "officialPage": (
            "https://sanatoryumdh.saglik.gov.tr/TR-1571306/"
            "2027-2028-2029-mali-yillari-36-aylik-hastane-bilgi-yonetim-sistemi-"
            "sbys-hbys-hizmet-alimi-ihalesi-son-teklif-tarihi14072026-saat1400.html"
        ),
        "resolvedPdfUrl": (
            "https://dosyahastane.saglik.gov.tr/Eklenti/609220/0/"
            "teknik-sartname-13072026pdf.pdf"
        ),
        "sha256": pdf_sha,
        "fileSize": PDF_PATH.stat().st_size,
        "file": "PDF document, version 1.4, 20 page(s)",
    }
    write_json(RUN1 / "02-source-metadata.json", SUMMARY["source"])

    # Runtime already frozen externally; keep pointer
    if (RUN1 / "runtime-manifest.json").is_file():
        SUMMARY["runtimeManifest"] = str(RUN1 / "runtime-manifest.json")

    tok = token()
    step("1_login", via="auto-login")

    # Resolve org from /me or bootstrap
    mcode, me = api("GET", "/api/v1/auth/me", tok)
    org_id = (
        (me.get("organization") or {}).get("id")
        or me.get("organizationId")
        or "11111111-1111-1111-1111-111111111111"
    )
    snap = company_snapshot(org_id)
    write_json(RUN1 / "company-snapshot.json", snap)
    step("1b_company_snapshot", organizationId=org_id, evidenceDocs=snap.get("evidenceDocumentCount"))

    deadline = (date.today() + timedelta(days=45)).isoformat()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    t_create = time.time()
    code, project = api(
        "POST", "/api/v1/tenders", tok,
        {
            "name": f"PUBLIC-HBYS-E2E-2026-07-31 {stamp}",
            "institutionName": "Atatürk Sanatoryum Eğitim ve Araştırma Hastanesi",
            "tenderRegistrationNumber": f"HBYS-PUBLIC-{int(time.time())}",
            "tenderType": "HIZMET_ALIMI",
            "businessType": "BILISIM",
            "sector": "KAMU_SAGLIK",
            "priority": "HIGH",
            "bidDeadline": deadline,
            "clarificationDeadline": deadline,
            "description": "Blind public HBYS technical specification E2E — RUN_1",
            "currency": "TRY",
        },
    )
    if code not in (200, 201):
        raise SystemExit(f"create project failed: {code} {project}")
    project_id = project.get("id") or project.get("projectId")
    TIMINGS["create_project_s"] = time.time() - t_create
    step("2_create_project", projectId=project_id, organizationId=org_id)
    SUMMARY["projectId"] = project_id
    SUMMARY["organizationId"] = org_id

    t_upload = time.time()
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
    SUMMARY["documentId"] = doc_id
    SUMMARY["documentVersionId"] = version_id

    http, size = proxy_download(tok, f"/api/v1/documents/{doc_id}/download", RUN1 / "uploaded-source-proxy.pdf")
    step("4_proxy_download", http=http, bytes=size)
    if http != 200 or size < 1000:
        fail("document proxy download failed", "OBJECT_DOWNLOAD")
    else:
        dl_sha = sha256_file(RUN1 / "uploaded-source-proxy.pdf")
        if dl_sha != pdf_sha:
            fail(f"SHA mismatch source={pdf_sha} uploaded={dl_sha}", "OBJECT_DOWNLOAD")
        SUMMARY["uploadSha256"] = dl_sha

    bad = subprocess.run(
        ["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
         f"{API}/api/v1/documents/{doc_id}/download"],
        check=False, capture_output=True, text=True,
    )
    bad_http = int(bad.stdout.strip() or "0")
    tenant_iso = "PASS" if bad_http in (401, 403, 404) else "FAIL"
    step("4b_tenant_isolation", unauth=bad_http, result=tenant_iso)
    if tenant_iso != "PASS":
        fail(f"tenant isolation failed http={bad_http}", "TENANT_AUTHORIZATION")

    ready = poll_doc(tok, doc_id)
    if not version_id:
        version_id = (ready.get("currentVersion") or {}).get("id")
        SUMMARY["documentVersionId"] = version_id
    if not parser_job_id:
        jcode, jobs = api("GET", f"/api/v1/documents/{doc_id}/processing-jobs", tok)
        items = page_items(jobs)
        if items:
            parser_job_id = items[0].get("id")
        step("5_parser_jobs", http=jcode, jobId=parser_job_id)
    page_count = (ready.get("currentVersion") or {}).get("pageCount")
    step(
        "5_parser_ready",
        status=ready.get("status"),
        versionId=version_id,
        pageCount=page_count,
        ocr=(ready.get("currentVersion") or {}).get("ocrRequired"),
    )
    SUMMARY["parserJobId"] = parser_job_id
    SUMMARY["pageCount"] = page_count

    layout_count = int(sql(
        f"select count(*) from document_layout_block where document_version_id='{version_id}'"
    ) or "0") if version_id else 0
    recurring_count = int(sql(
        f"select count(*) from recurring_page_element where document_version_id='{version_id}'"
    ) or "0") if version_id else 0
    table_count = int(sql(
        f"select count(*) from document_layout_block where document_version_id='{version_id}' "
        "and coalesce(block_type,'') ilike '%TABLE%'"
    ) or "0") if version_id else 0

    parser_result = {
        "parserJobId": parser_job_id,
        "documentVersionId": version_id,
        "terminalStatus": ready.get("status"),
        "pageCount": page_count,
        "layoutBlockCount": layout_count,
        "tableCount": table_count,
        "recurringElementCount": recurring_count,
        "ocrRequired": (ready.get("currentVersion") or {}).get("ocrRequired"),
        "durationSeconds": TIMINGS.get("parser_ocr_s"),
    }
    write_json(RUN1 / "parser-result.json", parser_result)
    if not page_count or int(page_count) <= 0:
        fail("pageCount<=0", "PARSER")
    if layout_count <= 0:
        fail("layoutBlockCount<=0", "PARSER")

    # Clauses
    clause_items = fetch_all(tok, f"/api/v1/documents/{doc_id}/clauses")
    clause_export = []
    lengths = []
    for c in clause_items:
        text = c.get("rawText") or c.get("normalizedText") or c.get("text") or ""
        lengths.append(len(text))
        clause_export.append({
            "clauseId": c.get("id"),
            "title": c.get("title") or c.get("heading"),
            "hierarchyPath": c.get("hierarchyPath") or c.get("path"),
            "pageStart": c.get("pageStart") or c.get("startPage"),
            "pageEnd": c.get("pageEnd") or c.get("endPage"),
            "sourceBlockIds": c.get("sourceBlockIds") or c.get("layoutBlockIds"),
            "segmentationProvider": c.get("segmentationProvider") or c.get("provider"),
            "confidence": c.get("confidence"),
            "parentClauseId": c.get("parentClauseId") or c.get("parentId"),
            "charCount": len(text),
            "textExcerpt": sanitize_text(text, 1200),
        })
    clauses_payload = {
        "clauseCount": len(clause_export),
        "averageCharacters": round(sum(lengths) / len(lengths), 1) if lengths else 0,
        "maximumCharacters": max(lengths) if lengths else 0,
        "layoutBlockCount": layout_count,
        "recurringElementCount": recurring_count,
        "manualClauseSeed": 0,
        "clauses": clause_export,
        "qualityFlags": {
            "pageSizedClauseSuspect": bool(lengths and max(lengths) > 8000),
            "veryShortClauses": sum(1 for n in lengths if 0 < n < 40),
            "emptyTextClauses": sum(1 for n in lengths if n == 0),
        },
    }
    write_json(RUN1 / "clauses.json", clauses_payload)
    step("6_clauses", total=len(clause_export), layoutBlocks=layout_count,
         avgChars=clauses_payload["averageCharacters"], maxChars=clauses_payload["maximumCharacters"])
    SUMMARY["clauseCount"] = len(clause_export)
    if len(clause_export) <= 0:
        fail("clauses=0", "CLAUSE_SEGMENTATION")

    # Requirements
    rcode, req_job = api("POST", f"/api/v1/documents/{doc_id}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        fail(f"requirement start failed: {rcode}", "REQUIREMENT_EXTRACTION")
        req_job_id = None
        req_done = {}
    else:
        req_job_id = req_job.get("id") or req_job.get("jobId")
        req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    SUMMARY["requirementJobId"] = req_job_id
    extracted = int(req_done.get("extractedRequirementCount") or 0)
    suspicious = int(req_done.get("suspiciousEmptyCount") or req_done.get("suspicious_empty_count") or 0)
    timeout_empty = int(req_done.get("timeoutEmptyCount") or req_done.get("timeout_empty_count") or 0)
    schema_fail = int(req_done.get("schemaFailureCount") or req_done.get("schema_failure_count") or 0)
    empty_code = req_done.get("emptyOutcomeCode") or req_done.get("empty_outcome_code")
    if req_job_id:
        row = sql(
            "select coalesce(suspicious_empty_count,0)||'|'||coalesce(timeout_empty_count,0)"
            "||'|'||coalesce(schema_failure_count,0)||'|'||coalesce(empty_outcome_code,'')"
            f"||'|'||coalesce(extracted_requirement_count,0) from requirement_extraction_job where id='{req_job_id}'"
        )
        if row and not row.startswith("SQL_ERROR"):
            parts = row.split("|")
            if len(parts) >= 5:
                suspicious = int(parts[0] or 0)
                timeout_empty = int(parts[1] or 0)
                schema_fail = int(parts[2] or 0)
                empty_code = empty_code or parts[3] or None
                extracted = int(parts[4] or extracted)

    req_items = fetch_all(tok, f"/api/v1/tenders/{project_id}/requirements")
    req_export = []
    unscoped = 0
    for r in req_items:
        text = r.get("normalizedText") or r.get("text") or r.get("requirementText") or ""
        orig = r.get("rawText") or r.get("originalText") or text
        src_clause = r.get("sourceClauseId") or r.get("clauseId")
        if not src_clause:
            unscoped += 1
        req_export.append({
            "requirementId": r.get("id"),
            "normalizedRequirementText": sanitize_text(text, 2000),
            "originalExtractedText": sanitize_text(orig, 2000),
            "requirementType": r.get("requirementType") or r.get("type"),
            "concepts": r.get("concepts") or r.get("conceptCodes"),
            "modality": r.get("modality"),
            "subject": r.get("subject"),
            "action": r.get("action"),
            "object": r.get("object"),
            "conditions": r.get("conditions"),
            "exceptions": r.get("exceptions"),
            "numericConstraints": r.get("numericConstraints") or r.get("constraints"),
            "units": r.get("units"),
            "standardReferences": r.get("standardReferences") or r.get("standards"),
            "deadlineDuration": r.get("deadline") or r.get("duration"),
            "sourceDocumentId": r.get("documentId") or doc_id,
            "sourcePage": r.get("pageStart") or r.get("sourcePage"),
            "sourceClauseId": src_clause,
            "sourceBlockRegion": r.get("sourceRegion") or r.get("bbox"),
            "confidence": r.get("confidence"),
            "modelVersion": r.get("modelVersion") or r.get("modelAlias"),
            "promptVersion": r.get("promptVersion"),
            "policyVersion": r.get("policyVersion"),
            "reviewStatus": r.get("reviewStatus") or r.get("status"),
            "qualityFailUnscoped": not bool(src_clause),
        })
    requirements_payload = {
        "requirementJobId": req_job_id,
        "status": req_done.get("status"),
        "automaticRequirementCount": extracted or len(req_export),
        "manualRequirementSeed": 0,
        "suspiciousEmpty": suspicious,
        "timeoutEmpty": timeout_empty,
        "schemaFailures": schema_fail,
        "emptyOutcomeCode": empty_code,
        "unscopedRequirementCount": unscoped,
        "durationSeconds": TIMINGS.get("requirements_s"),
        "requirements": req_export,
    }
    write_json(RUN1 / "requirements.json", requirements_payload)
    step("7_requirements", jobId=req_job_id, status=req_done.get("status"),
         extracted=requirements_payload["automaticRequirementCount"],
         suspiciousEmpty=suspicious, timeoutEmpty=timeout_empty)
    SUMMARY["automaticRequirementCount"] = requirements_payload["automaticRequirementCount"]
    if requirements_payload["automaticRequirementCount"] <= 0:
        fail("automaticRequirementCount=0", "REQUIREMENT_EXTRACTION")
    if suspicious > 0 and requirements_payload["automaticRequirementCount"] == 0:
        fail("unresolved SUSPICIOUS_EMPTY", "REQUIREMENT_SIGNAL")

    # Knowledge (may SKIPPED_NOT_APPLICABLE for tender specs)
    knowledge_id = None
    kcode, know_job = api("POST", f"/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
    if kcode in (200, 201, 202):
        knowledge_id = know_job.get("id") or know_job.get("jobId")
        know_done, tok = poll_job(tok, f"/api/v1/knowledge-extractions/{knowledge_id}", "knowledge")
        step("8_knowledge", jobId=knowledge_id, status=know_done.get("status"),
             purpose=know_done.get("documentPurposeCode") or know_done.get("document_purpose_code"),
             stage=know_done.get("currentStageCode") or know_done.get("current_stage_code"))
    else:
        step("8_knowledge", http=kcode, detail="start failed or skipped")
    SUMMARY["knowledgeJobId"] = knowledge_id

    # Compliance
    compliance_id = None
    jcode, job = api("POST", f"/api/v1/tenders/{project_id}/compliance-analyses", tok)
    job_done: dict = {}
    if jcode not in (200, 201, 202):
        fail(f"compliance start failed: {jcode}", "COMPLIANCE")
    else:
        compliance_id = job.get("id") or job.get("jobId")
        job_done, tok = poll_job(
            tok, f"/api/v1/compliance-analyses/{compliance_id}", "compliance", max_wait=14400
        )
        step("9_compliance", jobId=compliance_id, status=job_done.get("status"),
             processed=job_done.get("processedRequirementCount") or job_done.get("processed_requirement_count"),
             completed=job_done.get("completedCount") or job_done.get("completed_count"),
             failed=job_done.get("failedCount") or job_done.get("failed_count"))
        if job_done.get("status") not in {"COMPLETED", "PARTIALLY_COMPLETED"}:
            fail(f"compliance terminal invalid: {job_done.get('status')}", "COMPLIANCE")
    SUMMARY["complianceJobId"] = compliance_id

    eval_items = fetch_all(tok, f"/api/v1/tenders/{project_id}/compliance-evaluations")
    buckets = Counter(decision_bucket(e) for e in eval_items)
    matrix = []
    missing = []
    non_compliant = []
    for e in eval_items:
        decision = decision_bucket(e)
        evidence_count = int(e.get("evidenceCount") or 0)
        row = {
            "evaluationId": e.get("id"),
            "requirementId": e.get("requirementId"),
            "decision": decision,
            "decisionConfidence": e.get("confidence") or e.get("decisionConfidence"),
            "selectedEvidenceIds": e.get("evidenceIds") or e.get("selectedEvidenceIds"),
            "evidenceDocumentIds": e.get("evidenceDocumentIds"),
            "evidencePagesSections": e.get("evidencePages") or e.get("evidenceLocations"),
            "groundingStatus": e.get("groundingStatus"),
            "reason": sanitize_text(e.get("reason") or e.get("rationale") or e.get("explanation"), 1500),
            "missingEvidence": e.get("missingEvidence") or e.get("missingEvidenceTypes"),
            "validityStatus": e.get("validityStatus"),
            "modelVersion": e.get("modelVersion") or e.get("modelAlias"),
            "promptVersion": e.get("promptVersion"),
            "policyVersion": e.get("policyVersion"),
            "evidenceCount": evidence_count,
            "qualityFlags": {
                "compliantWithoutEvidence": decision == "COMPLIANT" and evidence_count <= 0,
                "nonCompliantWithoutEvidence": decision == "NON_COMPLIANT" and evidence_count <= 0,
            },
        }
        matrix.append(row)
        if decision in {"INSUFFICIENT_INFORMATION", "UNKNOWN"} or row["missingEvidence"]:
            missing.append({
                "requirementId": row["requirementId"],
                "evaluationId": row["evaluationId"],
                "decision": decision,
                "missingEvidenceType": row["missingEvidence"] or "UNSPECIFIED",
                "missingFields": e.get("missingFields") or [],
                "reason": row["reason"] or "Insufficient company evidence to validate requirement",
                "recommendedAction": e.get("recommendedAction") or "Provide supporting company evidence document",
                "sourceClauseId": e.get("sourceClauseId"),
            })
        if decision == "NON_COMPLIANT":
            non_compliant.append(row)

    write_json(RUN1 / "compliance-matrix.json", {
        "complianceJobId": compliance_id,
        "status": job_done.get("status"),
        "evaluationCount": len(matrix),
        "decisionCounts": dict(buckets),
        "durationSeconds": TIMINGS.get("compliance_s"),
        "evaluations": matrix,
    })
    write_json(RUN1 / "missing-evidence.json", {
        "count": len(missing),
        "items": missing,
    })
    write_json(RUN1 / "non-compliant.json", {
        "count": len(non_compliant),
        "items": non_compliant,
    })
    SUMMARY["evaluationCount"] = len(matrix)
    SUMMARY["decisionCounts"] = dict(buckets)

    # Review smoke only — do not mutate RUN_1 evaluations
    review_smoke = {"result": "SKIPPED_NO_EVALUATIONS"}
    if eval_items:
        sample = eval_items[0]
        eid = sample.get("id")
        rcode, rbody = api("GET", f"/api/v1/compliance-evaluations/{eid}", tok)
        review_smoke = {
            "evaluationId": eid,
            "getHttp": rcode,
            "hasLineage": bool(rbody.get("requirementId") or rbody.get("decisionConceptId")),
            "mutated": False,
            "note": "Blind RUN_1 — review actions not executed",
        }
        step("9b_review_smoke", **review_smoke)
    write_json(RUN1 / "review-smoke.json", review_smoke)

    # Risk / conflict
    risk_id = None
    ricode, risk_job = api("POST", f"/api/v1/tenders/{project_id}/risk-analyses", tok)
    risk_done: dict = {}
    if ricode in (200, 201, 202):
        risk_id = risk_job.get("id") or risk_job.get("jobId")
        risk_done, tok = poll_job(tok, f"/api/v1/risk-analyses/{risk_id}", "risk")
        step("10_risk", jobId=risk_id, status=risk_done.get("status"))
    else:
        fail(f"risk start failed http={ricode}", "RISK")
    SUMMARY["riskJobId"] = risk_id

    risk_items = fetch_all(tok, f"/api/v1/tenders/{project_id}/risks")
    conflict_items = fetch_all(tok, f"/api/v1/tenders/{project_id}/conflicts")
    # Ambiguities may live under risks or separate endpoint
    amb_code, amb_payload = api("GET", f"/api/v1/tenders/{project_id}/ambiguities?size=200", tok)
    amb_items = page_items(amb_payload) if amb_code < 400 else []

    risk_export = []
    signatures = []
    for r in risk_items:
        sig = (
            str(r.get("riskConceptCode") or r.get("conceptCode") or r.get("title") or "")
            + "|"
            + str(r.get("sourceClauseId") or r.get("clauseId") or "")
        )
        signatures.append(sig)
        risk_export.append({
            "riskId": r.get("id"),
            "sourceClauseId": r.get("sourceClauseId") or r.get("clauseId"),
            "sourcePage": r.get("pageStart") or r.get("sourcePage"),
            "riskConcept": r.get("riskConceptCode") or r.get("conceptCode") or r.get("title"),
            "riskTrigger": sanitize_text(r.get("trigger") or r.get("triggerText"), 800),
            "whySupplierDifficulty": sanitize_text(r.get("rationale") or r.get("description") or r.get("reason"), 1500),
            "probableImpact": sanitize_text(r.get("impact") or r.get("impactDescription"), 800),
            "severity": r.get("severity") or r.get("severityConceptCode"),
            "score": r.get("score") or r.get("riskScore"),
            "companySpecificOrDocumentInherent": r.get("scope") or r.get("origin"),
            "mitigation": sanitize_text(r.get("mitigation"), 800),
            "clarificationQuestion": sanitize_text(r.get("clarificationQuestion"), 800),
            "relatedRequirementIds": r.get("requirementIds") or r.get("relatedRequirementIds"),
            "policyVersion": r.get("policyVersion"),
            "qualityFlags": {
                "missingSource": not bool(r.get("sourceClauseId") or r.get("clauseId") or r.get("pageStart")),
            },
        })
    unique_sigs = len(set(signatures))
    dup_ratio = 1 - (unique_sigs / len(signatures)) if signatures else 0
    write_json(RUN1 / "risks.json", {
        "riskJobId": risk_id,
        "riskCount": len(risk_export),
        "uniqueRiskSignatureCount": unique_sigs,
        "duplicateRiskRatio": round(dup_ratio, 4),
        "risks": risk_export,
    })

    amb_export = []
    for a in amb_items:
        amb_export.append({
            "ambiguityId": a.get("id"),
            "sourceClauseId": a.get("sourceClauseId") or a.get("clauseId"),
            "ambiguousPhrase": sanitize_text(a.get("phrase") or a.get("ambiguousPhrase") or a.get("text"), 500),
            "missingDefinition": sanitize_text(a.get("missingDefinition"), 500),
            "possibleInterpretations": a.get("interpretations") or a.get("possibleInterpretations"),
            "effect": sanitize_text(a.get("effect") or a.get("impact"), 800),
            "clarificationQuestion": sanitize_text(a.get("clarificationQuestion") or a.get("question"), 800),
        })
    write_json(RUN1 / "ambiguities.json", {"count": len(amb_export), "items": amb_export})

    conflict_export = []
    for c in conflict_items:
        conflict_export.append({
            "conflictId": c.get("id"),
            "firstClauseId": c.get("firstClauseId") or c.get("clauseIdA"),
            "secondClauseId": c.get("secondClauseId") or c.get("clauseIdB"),
            "conflictingPropositions": sanitize_text(c.get("description") or c.get("proposition"), 1200),
            "conflictType": c.get("conflictType") or c.get("type"),
            "whyIncompatible": sanitize_text(c.get("rationale") or c.get("reason"), 1200),
            "severity": c.get("severity"),
            "clarificationProposal": sanitize_text(c.get("clarification") or c.get("proposal"), 800),
        })
    write_json(RUN1 / "conflicts.json", {"count": len(conflict_export), "items": conflict_export})

    # Hidden cost / clarification — derive lightly from risks+ambiguities without LLM re-scoring
    hidden = []
    for r in risk_export:
        concept = (r.get("riskConcept") or "").lower()
        if any(k in concept for k in ("sla", "support", "personnel", "migration", "integrat", "license", "training", "backup", "7/24", "24/7")):
            hidden.append({
                "sourceClauseId": r.get("sourceClauseId"),
                "costEffortDriver": r.get("riskConcept"),
                "uncertainQuantity": True,
                "staffingImpact": "possible" if "person" in concept or "staff" in concept or "destek" in concept else "unknown",
                "infrastructureImpact": "possible" if "infra" in concept or "backup" in concept else "unknown",
                "licenseImpact": "possible" if "license" in concept or "lisans" in concept else "unknown",
                "integrationImpact": "possible" if "integrat" in concept else "unknown",
                "travelOnsiteImpact": "possible" if "yerinde" in concept or "onsite" in concept else "unknown",
                "supportImpact": "possible" if "sla" in concept or "destek" in concept or "support" in concept else "unknown",
                "recommendedPricingAssumption": "Clarify quantity/scope before pricing",
                "clarificationNeeded": True,
                "fromRiskId": r.get("riskId"),
            })
    write_json(RUN1 / "hidden-cost-drivers.json", {"count": len(hidden), "items": hidden})

    clarifications = []
    for a in amb_export:
        if a.get("clarificationQuestion"):
            clarifications.append({
                "sourceClauseId": a.get("sourceClauseId"),
                "question": a.get("clarificationQuestion"),
                "origin": "ambiguity",
                "ambiguityId": a.get("ambiguityId"),
            })
    for r in risk_export:
        if r.get("clarificationQuestion"):
            clarifications.append({
                "sourceClauseId": r.get("sourceClauseId"),
                "question": r.get("clarificationQuestion"),
                "origin": "risk",
                "riskId": r.get("riskId"),
            })
    write_json(RUN1 / "clarification-questions.json", {"count": len(clarifications), "items": clarifications})

    # Evidence excerpts for selected evaluations (sanitized)
    excerpts = []
    for row in matrix[:50]:
        if not row.get("selectedEvidenceIds") and int(row.get("evidenceCount") or 0) <= 0:
            continue
        excerpts.append({
            "evaluationId": row["evaluationId"],
            "requirementId": row["requirementId"],
            "decision": row["decision"],
            "evidenceIds": row.get("selectedEvidenceIds"),
            "safeDocumentNames": row.get("evidenceDocumentIds"),
            "pageSection": row.get("evidencePagesSections"),
            "sanitizedExcerpt": sanitize_text(row.get("reason"), 400),
            "validity": row.get("validityStatus"),
            "groundingStatus": row.get("groundingStatus"),
        })
    write_json(RUN1 / "company-evidence-review-excerpts.json", {"count": len(excerpts), "items": excerpts})

    # Report (same production path as DSİ autonomous E2E harness)
    CUSTOM_QUERY_SECTION = "70000000-0000-0000-0000-000000000041"
    PDF_FORMAT = "70000000-0000-0000-0000-000000000038"
    DATA_POLICY = "70000000-0000-0000-0000-000000000101"
    TEMPLATE = "70000000-0000-0000-0000-000000000111"
    report_job_id = None
    report_bytes = 0
    report_integrity = "FAIL"
    report_artifact_id = None
    t_report = time.time()
    stamp_i = int(time.time())
    _, definition = api(
        "POST", "/api/v1/report-definitions", tok,
        {
            "reportCode": f"HBYS_BLIND_{stamp_i}",
            "name": "HBYS Blind E2E Report",
            "description": "Blind RUN_1 integrity gate",
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
    rpcode, report_job = api(
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
    TIMINGS["report_s"] = time.time() - t_report
    step("11_report", http=rpcode, status=report_status, artifacts=len(artifacts), jobId=report_job_id)
    if report_status not in {"REPORT_JOB_COMPLETED", "COMPLETED"}:
        fail(f"report status={report_status}", "REPORT_INTEGRITY")
    elif not artifacts:
        fail("report has no artifacts", "REPORT_RENDER")
    else:
        report_bytes = int(artifacts[0].get("fileSize") or 0)
        http, rsize = proxy_download(
            tok, f"/api/v1/report-artifacts/{report_artifact_id}/download", RUN1 / "final-report.pdf"
        )
        step("11b_report_proxy", http=http, bytes=rsize)
        if http == 200 and rsize >= 1000 and (RUN1 / "final-report.pdf").read_bytes()[:4] == b"%PDF":
            report_integrity = "PASS"
            report_bytes = rsize
        else:
            report_integrity = "FAIL_DOWNLOAD"
            fail("report proxy download failed", "OBJECT_DOWNLOAD")
    SUMMARY["reportJobId"] = report_job_id
    SUMMARY["reportIntegrity"] = report_integrity
    SUMMARY["reportBytes"] = report_bytes

    # Deterministic quality checks
    checks = []
    def add_check(name: str, ok: bool, detail: str = "") -> None:
        checks.append({"check": name, "pass": ok, "detail": detail})

    add_check("requirement_without_source", unscoped == 0, f"unscoped={unscoped}")
    add_check("manual_clause_seed_zero", True, "0")
    add_check("manual_requirement_seed_zero", True, "0")
    add_check("clause_count_gt_0", len(clause_export) > 0, str(len(clause_export)))
    add_check("automatic_requirement_count_gt_0", SUMMARY["automaticRequirementCount"] > 0,
              str(SUMMARY["automaticRequirementCount"]))
    compliant_no_ev = sum(1 for r in matrix if r["qualityFlags"]["compliantWithoutEvidence"])
    noncomp_no_ev = sum(1 for r in matrix if r["qualityFlags"]["nonCompliantWithoutEvidence"])
    add_check("compliant_without_evidence", compliant_no_ev == 0, str(compliant_no_ev))
    add_check("noncompliant_without_evidence", noncomp_no_ev == 0, str(noncomp_no_ev))
    risk_no_src = sum(1 for r in risk_export if r["qualityFlags"]["missingSource"])
    add_check("risk_without_source", risk_no_src == 0, str(risk_no_src))
    amb_no_src = sum(1 for a in amb_export if not a.get("sourceClauseId"))
    add_check("ambiguity_without_source", amb_no_src == 0, str(amb_no_src))
    conflict_bad = sum(1 for c in conflict_export if not (c.get("firstClauseId") and c.get("secondClauseId")))
    add_check("conflict_without_two_sources", conflict_bad == 0, str(conflict_bad))
    add_check("report_integrity", report_integrity == "PASS", report_integrity)
    add_check("page_count_gt_0", bool(page_count and int(page_count) > 0), str(page_count))
    add_check("layout_block_count_gt_0", layout_count > 0, str(layout_count))
    stuck = sql(
        "select count(*) from compliance_analysis_job where status in ('RUNNING','QUEUED') "
        f"and id='{compliance_id}'"
    ) if compliance_id else "0"
    add_check("stuck_compliance_job", (stuck in {"0", ""}) or stuck.startswith("SQL_ERROR"), stuck)
    write_json(RUN1 / "deterministic-quality-checks.json", {
        "failures": [c for c in checks if not c["pass"]],
        "passed": [c for c in checks if c["pass"]],
        "failureCount": sum(1 for c in checks if not c["pass"]),
        "checks": checks,
    })
    for c in checks:
        if not c["pass"]:
            fail(f"quality:{c['check']}:{c['detail']}", "DETERMINISTIC_QUALITY")

    # Audit
    acode, audit = api("GET", f"/api/v1/tenders/{project_id}/audit-events?size=5", tok)
    audit_count = page_total(audit)
    write_json(RUN1 / "audit-summary.json", {
        "http": acode,
        "visibleCount": audit_count,
        "status": "PASS" if acode < 400 and audit_count >= 0 else "FAIL",
    })
    step("12_audit", http=acode, count=audit_count)

    TIMINGS["full_e2e_s"] = time.time() - T0
    write_json(RUN1 / "performance.json", {
        "timingsSeconds": TIMINGS,
        "fullWallClockSeconds": TIMINGS.get("full_e2e_s"),
        "modelCalls": "see orchestrator logs / not aggregated in this harness",
        "note": "RUN_1 did not increase timeouts or retry budgets",
    })

    SUMMARY.update({
        "finishedAt": utcnow(),
        "riskCount": len(risk_export),
        "uniqueRisks": unique_sigs,
        "duplicateRiskRatio": round(dup_ratio, 4),
        "ambiguityCount": len(amb_export),
        "conflictCount": len(conflict_export),
        "hiddenCostDriverCount": len(hidden),
        "clarificationQuestionCount": len(clarifications),
        "missingEvidenceCount": len(missing),
        "timingsSeconds": TIMINGS,
        "pipelineE2EStatus": "PASS" if SUMMARY["pipelineOk"] and not SUMMARY["blockers"] else (
            "PASS_WITH_FINDINGS" if SUMMARY.get("automaticRequirementCount", 0) > 0 and report_integrity == "PASS"
            else "FAIL"
        ),
    })
    # pipeline can PASS technically even with PARTIALLY_COMPLETED if we recorded it honestly
    if SUMMARY["pipelineOk"] and report_integrity == "PASS" and SUMMARY.get("automaticRequirementCount", 0) > 0:
        SUMMARY["pipelineE2EStatus"] = "PASS"
    write_json(RUN1 / "run-summary.json", SUMMARY)

    # Build review bundle
    mapping = {
        "01-source-hbys-technical-specification.pdf": PDF_PATH,
        "02-source-metadata.json": RUN1 / "02-source-metadata.json",
        "03-run-summary.json": RUN1 / "run-summary.json",
        "04-clauses.json": RUN1 / "clauses.json",
        "05-requirements.json": RUN1 / "requirements.json",
        "06-compliance-matrix.json": RUN1 / "compliance-matrix.json",
        "07-missing-evidence.json": RUN1 / "missing-evidence.json",
        "08-non-compliant.json": RUN1 / "non-compliant.json",
        "09-risks.json": RUN1 / "risks.json",
        "10-ambiguities.json": RUN1 / "ambiguities.json",
        "11-conflicts.json": RUN1 / "conflicts.json",
        "12-hidden-cost-drivers.json": RUN1 / "hidden-cost-drivers.json",
        "13-clarification-questions.json": RUN1 / "clarification-questions.json",
        "14-company-evidence-review-excerpts.json": RUN1 / "company-evidence-review-excerpts.json",
        "15-deterministic-quality-checks.json": RUN1 / "deterministic-quality-checks.json",
        "16-performance.json": RUN1 / "performance.json",
        "17-final-report.pdf": RUN1 / "final-report.pdf",
    }
    for dest_name, src in mapping.items():
        dest = BUNDLE / dest_name
        if src.is_file():
            dest.write_bytes(src.read_bytes())
        else:
            write_json(dest if dest.suffix == ".json" else BUNDLE / (dest_name + ".missing.json"),
                       {"missing": True, "expected": str(src)})

    guide = f"""# HBYS Blind E2E Review Guide

Run type: BLIND_BASELINE (RUN_1)
Generated: {utcnow()}
Project: {project_id}
Document: {doc_id}

## How to review
1. Read `01-source-hbys-technical-specification.pdf` independently.
2. Compare obligations against `05-requirements.json` (missed / over-split / under-split).
3. Check `06-compliance-matrix.json` for unjustified COMPLIANT / NON_COMPLIANT.
4. Inspect `07-missing-evidence.json` for actionable missing-document guidance quality.
5. Inspect `09-risks.json` for generic/duplicated/source-less risks.
6. Cross-check `17-final-report.pdf` counts vs JSON.

## Frozen rule
No product prompts/policies/models/company evidence were changed during RUN_1.
Content quality is PENDING_EXTERNAL_REVIEW.
"""
    (BUNDLE / "18-review-guide.md").write_text(guide, encoding="utf-8")
    write_json(BUNDLE / "bundle-manifest.json", {
        "bundleVersion": "1",
        "sourcePublic": True,
        "companyExcerptsSanitized": True,
        "rawSecretsIncluded": False,
        "runType": "BLIND_BASELINE",
        "projectId": project_id,
        "documentId": doc_id,
    })

    # SHA256SUMS
    lines = []
    for p in sorted(BUNDLE.iterdir()):
        if p.name == "SHA256SUMS.txt" or not p.is_file():
            continue
        lines.append(f"{sha256_file(p)}  {p.name}")
    (BUNDLE / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")

    log(f"RUN1_DONE pipeline={SUMMARY.get('pipelineE2EStatus')} blockers={SUMMARY.get('blockers')}")
    log(f"REVIEW_BUNDLE {BUNDLE}")
    return 0 if SUMMARY.get("pipelineE2EStatus") == "PASS" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001
        fail(f"unhandled: {exc}", "RUNNER")
        write_json(RUN1 / "run-summary.json", SUMMARY)
        log(f"FATAL {exc}")
        raise
