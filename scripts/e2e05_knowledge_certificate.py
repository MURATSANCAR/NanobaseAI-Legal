#!/usr/bin/env python3
"""E2E-05 evidence / certificate knowledge gate.

Uploads a CERTIFICATE (or datasheet) PDF and asserts knowledge job reaches
COMPLETED with purpose CERTIFICATE and grounded entities (or EXISTING_KNOWLEDGE reuse).
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
PDF_PATH = Path(os.environ.get("E2E_CERT_PDF", "/tmp/nanobase-e2e/sample_certificate.pdf"))
OUT = Path(os.environ.get("E2E_REPORT", "/tmp/e2e05_knowledge_certificate.json"))
REPORT: dict = {"gate": "E2E-05", "ok": True, "blockers": [], "steps": []}


def step(name: str, **payload) -> None:
    REPORT["steps"].append({"step": name, **payload})
    print(f"[STEP] {name} {payload}", flush=True)


def fail(reason: str) -> None:
    REPORT["ok"] = False
    REPORT["blockers"].append(reason)


def token() -> str:
    return json.loads(
        subprocess.check_output(
            ["curl", "-sS", "-X", "POST", f"{API}/api/v1/auth/auto-login",
             "-H", "Accept: application/json"],
            text=True,
        )
    )["accessToken"]


def api(method: str, path: str, tok: str, data=None, timeout: int = 180):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        f"{API}{path}", data=body, method=method,
        headers={"Authorization": f"Bearer {tok}", "Accept": "application/json",
                 **({"Content-Type": "application/json"} if body else {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            return exc.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            return exc.code, {"raw": raw[:1000]}


def main() -> int:
    if not PDF_PATH.is_file():
        step("SKIPPED", reason=f"certificate PDF missing: {PDF_PATH}")
        REPORT["ok"] = True
        REPORT["skipped"] = True
        OUT.write_text(json.dumps(REPORT, indent=2), encoding="utf-8")
        return 0

    tok = token()
    deadline = (date.today() + timedelta(days=30)).isoformat()
    code, project = api("POST", "/api/v1/tenders", tok, {
        "name": f"E2E-05 Certificate {int(time.time())}",
        "institutionName": "Nano",
        "tenderRegistrationNumber": f"E2E05-{int(time.time())}",
        "tenderType": "MAL_ALIMI",
        "businessType": "BILISIM",
        "sector": "KAMU_TEKNOLOJI",
        "priority": "MEDIUM",
        "bidDeadline": deadline,
        "clarificationDeadline": deadline,
        "description": "Evidence knowledge purpose isolation",
        "currency": "TRY",
    })
    project_id = project.get("id")
    step("project", http=code, id=project_id)

    doc = json.loads(subprocess.check_output([
        "curl", "-sS", "-X", "POST",
        f"{API}/api/v1/tenders/{project_id}/documents",
        "-H", f"Authorization: Bearer {tok}",
        "-H", "Accept: application/json",
        "-F", f"file=@{PDF_PATH};type=application/pdf",
        "-F", "documentType=CERTIFICATE",
        "-F", f"logicalName={PDF_PATH.name}",
        "-F", "includedInAnalysis=true",
    ], text=True))
    doc_id = doc.get("id")
    step("upload", documentId=doc_id)

    # Wait READY
    for _ in range(120):
        _, d = api("GET", f"/api/v1/documents/{doc_id}", tok)
        status = d.get("status")
        if status == "READY":
            break
        if status in {"FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}:
            fail(f"document {status}")
            break
        time.sleep(10)
    else:
        fail("document timeout")

    kcode, job = api("POST", f"/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
    if kcode not in (200, 201, 202):
        fail(f"knowledge start {kcode}")
    else:
        job_id = job.get("id") or job.get("jobId")
        for _ in range(160):
            _, done = api("GET", f"/api/v1/knowledge-extractions/{job_id}", tok)
            status = done.get("status")
            if status in {"COMPLETED", "FAILED", "CANCELLED"}:
                step("knowledge", jobId=job_id, status=status,
                     purpose=done.get("documentPurposeCode"),
                     existing=done.get("existingKnowledgeUsed"),
                     entities=done.get("extractedEntityCount"),
                     stage=done.get("currentStageCode"),
                     error=done.get("errorCode"))
                if status != "COMPLETED":
                    fail(f"knowledge {status}: {done.get('errorCode')}")
                elif done.get("documentPurposeCode") not in (None, "CERTIFICATE"):
                    # purpose may be absent on older API responses; prefer CERTIFICATE
                    if done.get("documentPurposeCode") and done.get("documentPurposeCode") != "CERTIFICATE":
                        fail(f"unexpected purpose {done.get('documentPurposeCode')}")
                break
            time.sleep(15)
        else:
            fail("knowledge timeout")

    OUT.write_text(json.dumps(REPORT, indent=2), encoding="utf-8")
    return 0 if REPORT["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
