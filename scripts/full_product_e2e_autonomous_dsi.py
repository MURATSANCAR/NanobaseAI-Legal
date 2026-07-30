#!/usr/bin/env python3
"""E2E-01 autonomous DSİ gate — zero manual SQL seed / fixture assertion reuse.

Forbidden: manual clause/requirement SQL seed, counting seeded fixtures as PASS.
Required: clause>0, auto-extracted requirements>0, compliance terminal,
report integrity PASS (size>=1200, status COMPLETED), proxy download PASS.
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
from urllib.parse import urlparse

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
PDF_PATH = Path(
    os.environ.get(
        "E2E_PDF",
        "/tmp/nanobase-e2e/DSI_Sulama_Otomasyon_Genel_Teknik_Sartname.pdf",
    )
)
OUT = Path(os.environ.get("E2E_REPORT", "/tmp/full_product_autonomous_dsi_e2e.json"))
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

REPORT: dict = {
    "gate": "E2E-01-AUTONOMOUS-DSI",
    "ok": True,
    "blockers": [],
    "steps": [],
    "forbiddenActions": ["SQL_SEED_CLAUSES", "SQL_SEED_REQUIREMENTS", "FIXTURE_PROJECT_REUSE"],
}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(name: str, **payload) -> None:
    entry = {"step": name, "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), **payload}
    REPORT["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:600]}")


def fail(reason: str) -> None:
    REPORT["ok"] = False
    REPORT["blockers"].append(reason)
    step("BLOCKER", reason=reason)


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


def upload(tok: str, project_id: str, pdf: Path) -> dict:
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
        "documentType=TECHNICAL_SPECIFICATION",
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
        log(f"[{label}] http={code} status={status} t={int(time.time()-start)}s")
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


def assert_host_not_docker(url: str) -> None:
    host = (urlparse(url).hostname or "").lower()
    banned = ("actenora-prodlike-minio", "minio", "localhost.internal")
    if any(host == b or host.endswith("." + b) for b in banned if "." not in b):
        # allow only if explicitly configured; docker DNS names always fail
        if "actenora" in host or host == "minio":
            fail(f"presign host is Docker-internal: {host}")


def main() -> int:
    if not PDF_PATH.is_file():
        raise SystemExit(f"PDF missing: {PDF_PATH}")

    tok = token()
    step("1_login", via="auto-login")

    deadline = (date.today() + timedelta(days=45)).isoformat()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    code, project = api(
        "POST",
        "/api/v1/tenders",
        tok,
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
    doc_id = doc.get("id") or doc.get("documentId")
    step("3_upload", documentId=doc_id, bytes=PDF_PATH.stat().st_size)

    dcode, dl = api("GET", f"/api/v1/documents/{doc_id}/download-url", tok)
    url = dl.get("url") or ""
    step("4_presign", http=dcode, host=urlparse(url).hostname if url else None)
    if url:
        assert_host_not_docker(url)

    # Proxy download must work even when public MinIO is unreachable from browser.
    pcode = subprocess.run(
        [
            "curl",
            "-sS",
            "-o",
            f"/tmp/e2e_doc_{doc_id}.pdf",
            "-w",
            "%{http_code}",
            "-H",
            f"Authorization: Bearer {tok}",
            f"{API}/api/v1/documents/{doc_id}/download",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    proxy_http = int(pcode.stdout.strip() or "0")
    proxy_size = Path(f"/tmp/e2e_doc_{doc_id}.pdf").stat().st_size if proxy_http == 200 else 0
    step("4b_proxy_download", http=proxy_http, bytes=proxy_size)
    if proxy_http != 200 or proxy_size < 1000:
        fail("document proxy download failed")

    ready = poll_doc(tok, doc_id)
    step("5_parser_ready", status=ready.get("status"))

    ccode, clauses = api("GET", f"/api/v1/documents/{doc_id}/clauses?page=0&size=50", tok)
    clause_total = clauses.get("totalElements")
    if clause_total is None:
        clause_total = len(page_items(clauses))
    step("6_clauses", http=ccode, total=clause_total)
    if not clause_total or int(clause_total) <= 0:
        fail("clauses=0 — segmentation/provider chain failed (no SQL seed allowed)")

    rcode, req_job = api("POST", f"/api/v1/documents/{doc_id}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        raise SystemExit(f"requirement start failed: {rcode} {req_job}")
    req_job_id = req_job.get("id") or req_job.get("jobId")
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    extracted = int(req_done.get("extractedRequirementCount") or 0)
    step(
        "7_requirements",
        jobId=req_job_id,
        status=req_done.get("status"),
        extracted=extracted,
        emptyOutcome=req_done.get("emptyOutcomeCode"),
    )
    if extracted <= 0:
        fail("auto requirements=0 — not counting any seed")

    kcode, know_job = api("POST", f"/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
    if kcode in (200, 201, 202):
        know_id = know_job.get("id") or know_job.get("jobId")
        know_done, tok = poll_job(tok, f"/api/v1/knowledge-extractions/{know_id}", "knowledge")
        step(
            "8_knowledge",
            jobId=know_id,
            status=know_done.get("status"),
            entities=know_done.get("extractedEntityCount"),
            purpose=know_done.get("documentPurposeCode"),
            existingUsed=know_done.get("existingKnowledgeUsed"),
        )
        if know_done.get("status") == "FAILED":
            # Knowledge failure blocks FULL_PRODUCT_READY but not necessarily DI clause path.
            fail(f"knowledge FAILED: {know_done.get('errorCode')}")
    else:
        fail(f"knowledge start failed http={kcode}")

    jcode, job = api("POST", f"/api/v1/tenders/{project_id}/compliance-analyses", tok)
    if jcode not in (200, 201, 202):
        raise SystemExit(f"compliance start failed: {jcode} {job}")
    job_id = job.get("id") or job.get("jobId")
    job_done, tok = poll_job(tok, f"/api/v1/compliance-analyses/{job_id}", "compliance", max_wait=3600)
    step("9_compliance", jobId=job_id, status=job_done.get("status"))
    if job_done.get("status") != "COMPLETED":
        fail(f"compliance not COMPLETED: {job_done.get('status')}")

    ecode2, evals = api("GET", f"/api/v1/tenders/{project_id}/compliance-evaluations", tok)
    eval_items = page_items(evals)
    sample = eval_items[0] if eval_items else {}
    if sample.get("id"):
        decision = COMPLIANT if sample.get("evidenceCount") else PARTIALLY
        api(
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

    stamp_i = int(time.time())
    _, definition = api(
        "POST",
        "/api/v1/report-definitions",
        tok,
        {
            "reportCode": f"AUTON_E2E_{stamp_i}",
            "name": "Autonomous E2E Report",
            "description": "Integrity gate",
            "scope": "ORGANIZATION",
        },
    )
    def_id = definition.get("id")
    _, version = api(
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
                    "titleTemplate": "Project summary requirements compliance risks",
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
    status = report_job.get("statusConceptCode") or report_job.get("status")
    artifacts = report_job.get("artifacts") or []
    step("10_report", http=gcode, status=status, artifacts=len(artifacts), jobId=report_job.get("id"))
    if status not in {"REPORT_JOB_COMPLETED", "COMPLETED"}:
        fail(f"report integrity gate failed status={status} error={report_job.get('errorCode')}")
    if not artifacts:
        fail("report has no artifacts")
    else:
        art = artifacts[0]
        art_id = art.get("id")
        size = int(art.get("fileSize") or 0)
        if size < 1200:
            fail(f"report artifact too small: {size}")
        # Prefer authenticated proxy over presign for browser-safe download.
        proxy = subprocess.run(
            [
                "curl",
                "-sS",
                "-o",
                f"/tmp/e2e_report_{project_id}.pdf",
                "-w",
                "%{http_code}",
                "-H",
                f"Authorization: Bearer {tok}",
                f"{API}/api/v1/report-artifacts/{art_id}/download",
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        rhttp = int(proxy.stdout.strip() or "0")
        rsize = Path(f"/tmp/e2e_report_{project_id}.pdf").stat().st_size if rhttp == 200 else 0
        step("11_report_proxy", http=rhttp, bytes=rsize)
        if rhttp != 200 or rsize < 1200:
            fail("report proxy download failed or too small")
        with open(f"/tmp/e2e_report_{project_id}.pdf", "rb") as fh:
            magic = fh.read(4)
        if magic != b"%PDF":
            fail("report missing PDF magic")

    acode, audit = api("GET", "/api/v1/audit-events?page=0&size=20&sort=createdAt,desc", tok)
    step("12_audit", http=acode, count=len(page_items(audit)))

    REPORT["projectId"] = project_id
    REPORT["documentId"] = doc_id
    REPORT["requirementJobId"] = req_job_id
    REPORT["complianceJobId"] = job_id
    REPORT["reportJobId"] = report_job.get("id")
    OUT.write_text(json.dumps(REPORT, indent=2, ensure_ascii=False), encoding="utf-8")
    log(f"REPORT_WRITTEN {OUT} ok={REPORT['ok']} blockers={REPORT['blockers']}")
    return 0 if REPORT["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
