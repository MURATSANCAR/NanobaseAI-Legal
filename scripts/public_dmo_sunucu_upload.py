#!/usr/bin/env python3
"""Upload DMO Sunucu teknik şartname and export it-sartname-verify package."""

from __future__ import annotations

import hashlib
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8098"
PDF = Path("/tmp/dmo-sunucu-teknik.pdf")
OUT = Path("/tmp/it-verify")
UPLOAD_META = Path("/tmp/dmo-upload")


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
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000]}
        return exc.code, parsed


def di_get(path: str, timeout: int = 180) -> dict:
    raw = subprocess.check_output(
        [
            "sudo",
            "docker",
            "run",
            "--rm",
            "--network",
            "specai-legal-network",
            "curlimages/curl:8.5.0",
            "-sS",
            "-m",
            str(timeout),
            f"http://document-intelligence:8090{path}",
        ]
    )
    return json.loads(raw.decode())


def main() -> None:
    UPLOAD_META.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    assert PDF.exists() and PDF.stat().st_size > 1000, PDF

    tok = token()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    code, project = api(
        "POST",
        "/api/v1/tenders",
        tok,
        {
            "name": f"PUBLIC-DMO-SUNUCU-{stamp}",
            "institutionName": "Devlet Malzeme Ofisi",
            "tenderRegistrationNumber": f"DMO-11084-{int(time.time())}",
            "tenderType": "MAL_ALIMI",
            "businessType": "BILISIM",
            "sector": "KAMU_BILISIM",
            "priority": "HIGH",
            "bidDeadline": "2026-12-31",
            "clarificationDeadline": "2026-12-15",
            "description": "Public DMO Sunucu Alımı Teknik Şartnamesi IT verify package",
            "currency": "TRY",
        },
    )
    print("project", code, project.get("id") or project)
    if code not in (200, 201):
        raise SystemExit(project)
    project_id = project.get("id") or project.get("projectId")

    doc = json.loads(
        subprocess.check_output(
            [
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
                f"file=@{PDF};type=application/pdf",
                "-F",
                "documentType=TECHNICAL_SPECIFICATION",
                "-F",
                "logicalName=DMO-11084-Sunucu-Teknik-Sartname.pdf",
                "-F",
                "includedInAnalysis=true",
            ],
            text=True,
        )
    )
    (UPLOAD_META / "upload.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2))
    doc_id = doc.get("id") or doc.get("documentId")
    print("documentId", doc_id)

    ready = None
    t0 = time.time()
    while time.time() - t0 < 1800:
        _code, last = api("GET", f"/api/v1/documents/{doc_id}", tok)
        status = last.get("status")
        job = last.get("currentJob") or {}
        print(
            f"poll t={int(time.time()-t0)}s status={status} "
            f"job={job.get('status')} progress={job.get('progress')} "
            f"msg={(job.get('message') or '')[:120]}"
        )
        if status in {"READY", "FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}:
            ready = last
            break
        time.sleep(3)
    if ready is None:
        raise SystemExit("timeout")
    (UPLOAD_META / "document.json").write_text(json.dumps(ready, ensure_ascii=False, indent=2))
    print("FINAL", ready.get("status"))

    _jcode, jobs = api("GET", f"/api/v1/documents/{doc_id}/processing-jobs", tok)
    items = []
    if isinstance(jobs, dict):
        items = jobs.get("content") or jobs.get("items") or jobs.get("data") or []
    elif isinstance(jobs, list):
        items = jobs
    external = None
    for item in items:
        if item.get("externalReference"):
            external = item["externalReference"]
            break
    if not external:
        external = (ready.get("currentJob") or {}).get("externalReference")
    print("DI_JOB", external)

    meta = {
        "projectId": project_id,
        "documentId": doc_id,
        "status": ready.get("status"),
        "externalReference": external,
        "pageCount": (ready.get("currentVersion") or {}).get("pageCount"),
    }
    (UPLOAD_META / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2))

    result = di_get(f"/v1/jobs/{external}/result", timeout=180)
    (UPLOAD_META / "di-result.json").write_text(
        json.dumps(result, ensure_ascii=False), encoding="utf-8"
    )

    # Export package via side script for clarity
    (OUT / "raw-result-pointer.json").write_text(
        json.dumps({"jobId": external, **meta}, ensure_ascii=False, indent=2)
    )
    print(json.dumps(meta, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
