#!/usr/bin/env python3
"""Wait for requirement job then run company-fit for Innova E2E."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8098"
ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "4b2b6d32-3e96-4199-a4a2-8f1ea40041bb"
TEKNIK = "f823974a-3be6-4c52-b6ea-2a608dd0ed21"
JOB = "39c80531-cc2d-453c-bf5e-fbeed82911ec"
OUT = Path("/tmp/innova-e2e/out")
TERMINAL = {"COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"}


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
    url = path if path.startswith("http") else API + path
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


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    tok = token()
    t0 = time.time()
    last: dict = {}
    while time.time() - t0 < 3600:
        code, last = api("GET", f"/api/v1/requirement-extractions/{JOB}", tok)
        status = last.get("status")
        print(
            f"[requirements] status={status} extracted={last.get('extractedRequirementCount')} "
            f"processed={last.get('processedClauseCount')}/{last.get('totalClauseCount')} "
            f"t={int(time.time()-t0)}s",
            flush=True,
        )
        if status in TERMINAL:
            break
        if code == 401:
            tok = token()
        time.sleep(15)
    else:
        print("TIMEOUT", last, flush=True)
        return 1

    code, reqs = api(
        "GET",
        f"/api/v1/tenders/{PROJECT}/requirements?size=100&sort=createdAt,desc",
        tok,
    )
    items = page_items(reqs)
    print(f"[requirements_list] count={len(items)}", flush=True)

    code, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
    cap_count = len(caps) if isinstance(caps, list) else 0
    print(f"[capabilities] count={cap_count}", flush=True)

    fcode, fit = api(
        "POST",
        f"/api/v1/tenders/{TEKNIK}/company-fit",
        tok,
        {"organizationId": ORG},
    )
    print(
        f"[company_fit] http={fcode} overall={fit.get('overall')} "
        f"must={fit.get('mustMet')}/{fit.get('mustTotal')} score={fit.get('overallScore')}",
        flush=True,
    )

    report = {
        "ok": fcode == 200 and len(items) > 0,
        "projectId": PROJECT,
        "teknikDocumentId": TEKNIK,
        "requirementJobId": JOB,
        "requirementJobStatus": last.get("status"),
        "requirementCount": len(items),
        "capabilityCount": cap_count,
        "fit": {
            "overall": fit.get("overall"),
            "mustMet": fit.get("mustMet"),
            "mustTotal": fit.get("mustTotal"),
            "overallScore": fit.get("overallScore"),
            "missingCritical": fit.get("missingCritical"),
        },
        "portal": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/expert/company-fit",
        "docs": {
            "teknik": "DMO Ihale 17004 Teknik Sartname (gercek)",
            "idari": "DMO Ihale 17004 Ticari Sartname (gercek)",
            "sozlesme": "Taslak sozlesme (uretilmis)",
            "company": "Innova ISO27001/9001/20000 + yetki + katalog (uretilmis)",
        },
    }
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    (OUT / "fit.json").write_text(json.dumps(fit, ensure_ascii=False, indent=2))
    (OUT / "capabilities.json").write_text(json.dumps(caps, ensure_ascii=False, indent=2))
    (OUT / "requirements-sample.json").write_text(
        json.dumps(items[:25], ensure_ascii=False, indent=2)
    )
    print(json.dumps(report, ensure_ascii=False), flush=True)
    print("PASS" if report["ok"] else "FAIL", flush=True)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
