#!/usr/bin/env python3
"""Resume Innova E2E after teknik READY — remaining docs + requirements + fit."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
ORG = os.environ.get("SPECAI_ORG_ID", "11111111-1111-1111-1111-111111111111")
PROJECT = "4b2b6d32-3e96-4199-a4a2-8f1ea40041bb"
TEKNIK = "f823974a-3be6-4c52-b6ea-2a608dd0ed21"
OUT = Path("/tmp/innova-e2e/out")
DOC_READY = {"READY"}
DOC_FAIL = {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}
TERMINAL = {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}
REPORT: dict = {"ok": True, "steps": [], "projectId": PROJECT, "resumed": True}


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


def page_items(payload) -> list:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("content", "items", "elements", "data"):
            if isinstance(payload.get(key), list):
                return payload[key]
    return []


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
            raise SystemExit(f"{label} failed: {status}")
        time.sleep(8)
    raise SystemExit(f"{label} timeout: {last}")


def poll_job(tok: str, path: str, label: str, max_wait: int = 3600) -> tuple[dict, str]:
    start = time.time()
    last: dict = {}
    while time.time() - start < max_wait:
        code, last = api("GET", path, tok)
        status = last.get("status")
        extra = last.get("extractedRequirementCount") or last.get("errorCode")
        log(f"[{label}] http={code} status={status} extra={extra} t={int(time.time()-start)}s")
        if status in TERMINAL:
            return last, tok
        if code == 401:
            tok = token()
        time.sleep(12)
    raise SystemExit(f"{label} timeout: {last}")


def collect_text(tok: str, document_id: str) -> str:
    chunks = []
    page = 0
    total = 1
    while page < total and page < 30:
        c, batch = api("GET", f"/api/v1/documents/{document_id}/pages?page={page}&size=50", tok)
        if c != 200:
            break
        total = max(batch.get("totalPages") or 1, 1)
        for p in page_items(batch):
            t = (p.get("normalizedText") or p.get("rawText") or "").strip()
            if t:
                chunks.append(t)
        page += 1
    text = "\n\n".join(chunks).strip()
    if len(text) >= 80:
        return text
    c, clauses = api("GET", f"/api/v1/documents/{document_id}/clauses?size=300", tok)
    return "\n\n".join(
        (x.get("normalizedText") or x.get("rawText") or "").strip()
        for x in page_items(clauses)
    ).strip()


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    tok = token()
    step("resume_login", tokenPrefix=tok[:16])

    code, docs = api("GET", f"/api/v1/tenders/{PROJECT}/documents?page=0&size=50", tok)
    items = page_items(docs)
    step("list_docs", http=code, count=len(items))

    ready = []
    for d in items:
        doc_id = d.get("id")
        label = d.get("logicalName") or doc_id
        dtype = d.get("documentType")
        if d.get("status") in DOC_READY:
            ready.append(d)
            step("already_ready", documentId=doc_id, documentType=dtype, logicalName=label)
            continue
        polled = poll_doc(tok, doc_id, label)
        ready.append(polled)
        step("ready", documentId=doc_id, documentType=dtype, logicalName=label, status=polled.get("status"))

    # capabilities via auto-wire or manual ingest
    time.sleep(3)
    code, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
    step("capabilities_autowire", http=code, count=len(caps) if isinstance(caps, list) else 0)

    if not isinstance(caps, list) or len(caps) < 2:
        ingest_docs = []
        for d in ready:
            dtype = d.get("documentType")
            if dtype not in {"CERTIFICATE", "OTHER", "PRODUCT_CATALOG", "FINANCIAL_FORM"}:
                continue
            text = collect_text(tok, d["id"])
            if text:
                ingest_docs.append(
                    {
                        "documentId": d["id"],
                        "docType": dtype,
                        "title": d.get("logicalName"),
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
            step(
                "manual_ingest",
                http=code,
                capabilityCount=ingest.get("capabilityCount"),
                docs=len(ingest_docs),
            )
            code, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
            step("capabilities", http=code, count=len(caps) if isinstance(caps, list) else 0)

    # requirements on teknik
    rcode, req_job = api("POST", f"/api/v1/documents/{TEKNIK}/requirement-extractions", tok, {})
    if rcode not in (200, 201, 202):
        raise SystemExit(f"req start failed {rcode} {req_job}")
    req_job_id = req_job.get("id") or req_job.get("jobId")
    step("requirement_start", http=rcode, jobId=req_job_id)
    req_done, tok = poll_job(tok, f"/api/v1/requirement-extractions/{req_job_id}", "requirements")
    step(
        "requirement_done",
        status=req_done.get("status"),
        extracted=req_done.get("extractedRequirementCount"),
    )

    code, reqs = api(
        "GET",
        f"/api/v1/tenders/{PROJECT}/requirements?size=100&sort=createdAt,desc",
        tok,
    )
    req_items = page_items(reqs)
    step("requirements_list", http=code, count=len(req_items))
    if not req_items:
        REPORT["ok"] = False

    fcode, fit = api(
        "POST",
        f"/api/v1/tenders/{TEKNIK}/company-fit",
        tok,
        {"organizationId": ORG},
    )
    step(
        "company_fit",
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

    REPORT["teknikDocumentId"] = TEKNIK
    REPORT["portal"] = f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/expert/company-fit"
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
        json.dumps(req_items[:25], ensure_ascii=False, indent=2)
    )

    ok = REPORT["ok"] and fcode == 200 and len(req_items) > 0
    log(
        f"[{'PASS' if ok else 'FAIL'}] project={PROJECT} overall={fit.get('overall')} "
        f"reqs={len(req_items)} caps={REPORT['capabilityCount']}"
    )
    print(
        json.dumps(
            {
                "ok": ok,
                "projectId": PROJECT,
                "fit": REPORT["fit"],
                "requirements": len(req_items),
                "capabilities": REPORT["capabilityCount"],
                "portal": REPORT["portal"],
            },
            ensure_ascii=False,
        )
    )
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
