#!/usr/bin/env python3
"""Upload public DISKI short PDF via portal API and collect DI job evidence."""

from __future__ import annotations

import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8098"
PDF = Path("/tmp/short-diski.pdf")
OUT = Path("/tmp/diski-short")


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


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    tok = token()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    code, project = api(
        "POST",
        "/api/v1/tenders",
        tok,
        {
            "name": f"PUBLIC-DISKI-SHORT-{stamp}",
            "institutionName": "Diyarbakır Su ve Kanalizasyon İdaresi",
            "tenderRegistrationNumber": f"DISKI-88-{int(time.time())}",
            "tenderType": "MAL_ALIMI",
            "businessType": "ALTYAPI",
            "sector": "KAMU_SU",
            "priority": "HIGH",
            "bidDeadline": "2026-12-31",
            "clarificationDeadline": "2026-12-15",
            "description": "Public DISKI Teknik-Sartname-88 short PDF gate check",
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
                "logicalName=Teknik-Sartname-88-DISKI.pdf",
                "-F",
                "includedInAnalysis=true",
            ],
            text=True,
        )
    )
    (OUT / "upload.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2))
    doc_id = doc.get("id") or doc.get("documentId")
    print("documentId", doc_id, "status", doc.get("status"))

    ready = None
    t0 = time.time()
    while time.time() - t0 < 900:
        code, last = api("GET", f"/api/v1/documents/{doc_id}", tok)
        status = last.get("status")
        job = last.get("currentJob") or {}
        msg = (job.get("message") or last.get("message") or "")[:140]
        print(
            f"poll status={status} jobStatus={job.get('status')} "
            f"progress={job.get('progress')} provider={job.get('provider')} msg={msg}"
        )
        if status in {"READY", "FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED"}:
            ready = last
            break
        time.sleep(3)

    if ready is None:
        raise SystemExit("timeout waiting for document")
    (OUT / "document.json").write_text(json.dumps(ready, ensure_ascii=False, indent=2))
    print("FINAL", ready.get("status"))

    jcode, jobs = api("GET", f"/api/v1/documents/{doc_id}/processing-jobs", tok)
    (OUT / "processing-jobs.json").write_text(json.dumps(jobs, ensure_ascii=False, indent=2))
    if isinstance(jobs, dict):
        items = jobs.get("content") or jobs.get("items") or jobs.get("data") or []
    elif isinstance(jobs, list):
        items = jobs
    else:
        items = []

    external = None
    for item in items:
        print(
            "job",
            item.get("id"),
            item.get("status"),
            item.get("provider"),
            item.get("externalReference"),
            item.get("progress"),
        )
        if item.get("externalReference"):
            external = item.get("externalReference")

    meta = {
        "projectId": project_id,
        "documentId": doc_id,
        "status": ready.get("status"),
        "externalReference": external,
        "currentJob": ready.get("currentJob"),
    }
    (OUT / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2))
    print(json.dumps(meta, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
