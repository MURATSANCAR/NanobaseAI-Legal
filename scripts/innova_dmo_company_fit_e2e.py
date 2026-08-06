#!/usr/bin/env python3
"""Prod-level Innova × DMO IT tender company-fit E2E on nanobase."""
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
ORG = os.environ.get("SPECAI_ORG_ID", "11111111-1111-1111-1111-111111111111")
ROOT = Path("/tmp/innova-e2e")
TENDER = ROOT / "tender"
COMPANY = ROOT / "company"
OUT = ROOT / "out"
DOC_READY = {"READY"}
DOC_FAIL = {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}
TERMINAL = {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}
REPORT: dict = {"ok": True, "steps": []}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(step_name: str, **payload) -> None:
    entry = {
        "step": step_name,
        "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        **payload,
    }
    REPORT["steps"].append(entry)
    log(f"[STEP] {step_name} {json.dumps(payload, ensure_ascii=False)[:700]}")


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


def upload(tok: str, project_id: str, pdf: Path, document_type: str, logical_name: str) -> dict:
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
        f"logicalName={logical_name}",
        "-F",
        "includedInAnalysis=true",
    ]
    return json.loads(subprocess.check_output(cmd, text=True))


def poll_doc(tok: str, doc_id: str, label: str, max_wait: int = 2400) -> dict:
    start = time.time()
    last: dict = {}
    while time.time() - start < max_wait:
        code, last = api("GET", f"/api/v1/documents/{doc_id}", tok)
        status = last.get("status")
        log(f"[doc:{label}] status={status} http={code} t={int(time.time()-start)}s")
        if status in DOC_READY:
            return last
        if status in DOC_FAIL:
            raise SystemExit(f"{label} failed: {status} {json.dumps(last, ensure_ascii=False)[:500]}")
        time.sleep(8)
    raise SystemExit(f"{label} not READY after {max_wait}s: {last}")


def poll_job(tok: str, path: str, label: str, max_wait: int = 3600) -> tuple[dict, str]:
    start = time.time()
    last: dict = {}
    while time.time() - start < max_wait:
        code, last = api("GET", path, tok)
        status = last.get("status")
        extra = last.get("extractedRequirementCount") or last.get("errorCode") or last.get("errorMessage")
        log(f"[{label}] http={code} status={status} extra={extra} t={int(time.time()-start)}s")
        if status in TERMINAL:
            return last, tok
        if code == 401:
            tok = token()
        time.sleep(12)
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
    OUT.mkdir(parents=True, exist_ok=True)
    teknik = next(TENDER.glob("01-teknik*.pdf"), None)
    idari = next(TENDER.glob("02-*.pdf"), None)
    sozlesme = next(TENDER.glob("03-*.pdf"), None)
    company_pdfs = sorted(COMPANY.glob("*.pdf"))
    if not teknik or not idari or not sozlesme or not company_pdfs:
        raise SystemExit(f"missing docs teknik={teknik} idari={idari} sozlesme={sozlesme} company={company_pdfs}")

    tok = token()
    step("1_login", tokenPrefix=tok[:16])

    stamp = time.strftime("%Y%m%d-%H%M%S")
    deadline = (date.today() + timedelta(days=60)).isoformat()
    code, project = api(
        "POST",
        "/api/v1/tenders",
        tok,
        {
            "name": f"INNOVA-DMO-IT-E2E-{stamp}",
            "institutionName": "Devlet Malzeme Ofisi / İhtiyaç Sahibi Kurum",
            "tenderRegistrationNumber": f"DMO-IT-INNOVA-{int(time.time())}",
            "tenderType": "MAL_ALIMI",
            "businessType": "BILISIM",
            "sector": "KAMU_BILISIM",
            "priority": "HIGH",
            "bidDeadline": deadline,
            "clarificationDeadline": deadline,
            "description": (
                "Prod E2E: gerçek DMO IT sunucu teknik + idari/ticari şartname + taslak sözleşme; "
                "teklif sahibi olarak İnnova Bilişim Çözümleri A.Ş. simülasyonu."
            ),
            "currency": "TRY",
        },
    )
    if code not in (200, 201):
        raise SystemExit(f"project create failed: {code} {project}")
    project_id = project.get("id") or project.get("projectId")
    step("2_project", projectId=project_id, projectName=project.get("name"))

    uploads = []
    for pdf, dtype, lname in [
        (teknik, "TECHNICAL_SPECIFICATION", "DMO-Sunucu-Teknik-Sartname.pdf"),
        (idari, "ADMINISTRATIVE_SPECIFICATION", "DMO-Idari-Ticari-Sartname.pdf"),
        (sozlesme, "DRAFT_CONTRACT", "Taslak-Sozlesme-Sunucu-Alimi.pdf"),
        (company_pdfs[0], "CERTIFICATE", "Innova-ISO27001-Certificate.pdf"),
        (
            next((p for p in company_pdfs if "9001" in p.name or "20000" in p.name), company_pdfs[1]),
            "CERTIFICATE",
            "Innova-ISO9001-ISO20000-Certificate.pdf",
        ),
        (
            next((p for p in company_pdfs if "yetki" in p.name), company_pdfs[2]),
            "OTHER",
            "Innova-Yetki-Partnerlik.pdf",
        ),
        (
            next((p for p in company_pdfs if "katalog" in p.name), company_pdfs[-1]),
            "PRODUCT_CATALOG",
            "Innova-Urun-Katalog-Ozet.pdf",
        ),
    ]:
        doc = upload(tok, project_id, pdf, dtype, lname)
        doc_id = doc.get("id") or doc.get("documentId")
        uploads.append({"id": doc_id, "type": dtype, "name": lname, "file": pdf.name, "bytes": pdf.stat().st_size})
        step("3_upload", documentId=doc_id, type=dtype, logicalName=lname, bytes=pdf.stat().st_size)

    ready_docs = []
    for item in uploads:
        doc = poll_doc(tok, item["id"], item["name"])
        item["status"] = doc.get("status")
        ready_docs.append(item)
        step("4_ready", **item)

    teknik_id = next(x["id"] for x in ready_docs if x["type"] == "TECHNICAL_SPECIFICATION")

    # Give auto-ingest a moment for company docs
    time.sleep(5)
    code, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
    step("5_capabilities_after_autowire", http=code, count=len(caps) if isinstance(caps, list) else 0)

    # Manual ingest fallback if auto-wire produced nothing useful
    if not isinstance(caps, list) or len(caps) < 2:
        ingest_docs = []
        for item in ready_docs:
            if item["type"] not in {"CERTIFICATE", "OTHER", "PRODUCT_CATALOG", "FINANCIAL_FORM"}:
                continue
            # pull pages text
            chunks = []
            page = 0
            total = 1
            while page < total and page < 20:
                c, batch = api("GET", f"/api/v1/documents/{item['id']}/pages?page={page}&size=50", tok)
                if c != 200:
                    break
                total = max(batch.get("totalPages") or 1, 1)
                for p in page_items(batch):
                    t = (p.get("normalizedText") or p.get("rawText") or "").strip()
                    if t:
                        chunks.append(t)
                page += 1
            text = "\n\n".join(chunks).strip()
            if len(text) < 40:
                # clauses fallback
                c, clauses = api("GET", f"/api/v1/documents/{item['id']}/clauses?size=200", tok)
                text = "\n\n".join(
                    (x.get("normalizedText") or x.get("rawText") or "").strip()
                    for x in page_items(clauses)
                ).strip()
            if text:
                ingest_docs.append(
                    {
                        "documentId": item["id"],
                        "docType": item["type"],
                        "title": item["name"],
                        "text": text,
                    }
                )
        if ingest_docs:
            code, ingest = api(
                "POST",
                f"/api/v1/organizations/{ORG}/capabilities/ingest",
                tok,
                {"documents": ingest_docs},
            )
            step("5b_manual_ingest", http=code, capabilityCount=ingest.get("capabilityCount"), docs=len(ingest_docs))
            code, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
            step("5c_capabilities", http=code, count=len(caps) if isinstance(caps, list) else 0)

    # Requirement extraction on teknik şartname
    rcode, req_job = api("POST", f"/api/v1/documents/{teknik_id}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        raise SystemExit(f"requirement extraction start failed: {rcode} {req_job}")
    req_job_id = req_job.get("id") or req_job.get("jobId")
    step("6_requirement_start", http=rcode, jobId=req_job_id)
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    step(
        "7_requirement_done",
        status=req_done.get("status"),
        extracted=req_done.get("extractedRequirementCount"),
    )

    code, reqs = api(
        "GET",
        f"/api/v1/tenders/{project_id}/requirements?size=100&sort=createdAt,desc",
        tok,
    )
    req_items = page_items(reqs)
    step("8_requirements_list", http=code, count=len(req_items))
    if len(req_items) < 1:
        REPORT["ok"] = False
        step("ABORT", reason="no requirements extracted")
        (OUT / "report.json").write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
        return 1

    # Company fit evaluate (auto-wire may have already run; force fresh)
    fcode, fit = api(
        "POST",
        f"/api/v1/tenders/{teknik_id}/company-fit",
        tok,
        {"organizationId": ORG},
    )
    step(
        "9_company_fit",
        http=fcode,
        overall=fit.get("overall"),
        mustMet=fit.get("mustMet"),
        mustTotal=fit.get("mustTotal"),
        score=fit.get("overallScore"),
        missing=fit.get("missingCritical"),
        rows=len(fit.get("rows") or []),
    )
    if fcode != 200:
        REPORT["ok"] = False

    lcode, latest = api("GET", f"/api/v1/tenders/{teknik_id}/company-fit", tok)
    step("10_latest_fit", http=lcode, reports=len(latest) if isinstance(latest, list) else 0)

    REPORT["projectId"] = project_id
    REPORT["teknikDocumentId"] = teknik_id
    REPORT["portal"] = f"https://portal.nanobase.ai/legal/#/project/{project_id}/expert/company-fit"
    REPORT["fit"] = {
        "overall": fit.get("overall"),
        "mustMet": fit.get("mustMet"),
        "mustTotal": fit.get("mustTotal"),
        "overallScore": fit.get("overallScore"),
        "missingCritical": fit.get("missingCritical"),
    }
    REPORT["capabilityCount"] = len(caps) if isinstance(caps, list) else 0
    REPORT["requirementCount"] = len(req_items)
    (OUT / "report.json").write_text(json.dumps(REPORT, ensure_ascii=False, indent=2))
    (OUT / "fit.json").write_text(json.dumps(fit, ensure_ascii=False, indent=2))
    (OUT / "capabilities.json").write_text(json.dumps(caps, ensure_ascii=False, indent=2))
    (OUT / "requirements-sample.json").write_text(
        json.dumps(req_items[:20], ensure_ascii=False, indent=2)
    )

    ok = (
        REPORT["ok"]
        and fcode == 200
        and fit.get("overall") in {"FIT", "CONDITIONAL", "NOT_FIT", "INSUFFICIENT_DATA"}
        and len(req_items) > 0
    )
    log(f"[{'PASS' if ok else 'FAIL'}] innova-dmo e2e project={project_id} overall={fit.get('overall')}")
    print(json.dumps({"ok": ok, "projectId": project_id, "fit": REPORT["fit"], "requirements": len(req_items), "capabilities": REPORT["capabilityCount"]}, ensure_ascii=False))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
