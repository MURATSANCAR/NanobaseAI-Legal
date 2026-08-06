#!/usr/bin/env python3
"""Company-fit production smoke (API + optional auto-wire class presence).

Flow: auto-login → ingest certificate text → list capabilities → evaluate fit
with inline requirements → GET latest report.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
ORG = os.environ.get(
    "SPECAI_ORG_ID", "11111111-1111-1111-1111-111111111111"
)


def log(msg: str) -> None:
    print(msg, flush=True)


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


def api(method: str, path: str, tok: str, data=None, timeout: int = 60):
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


def main() -> int:
    started = time.time()
    tok = token()
    log(f"[ok] login token={tok[:16]}…")

    evidence_id = str(uuid.uuid4())
    tender_id = str(uuid.uuid4())

    ingest_body = {
        "documents": [
            {
                "documentId": evidence_id,
                "docType": "CERTIFICATE",
                "title": "ISO + DIMM evidence",
                "text": (
                    "ISO 27001 Information Security Management System certificate. "
                    "Geçerlilik: 31.12.2027. Yetkili partner belgesi. "
                    "Sunucu konfigürasyonu en az 16 DIMM destekler. "
                    "TSE/CE uygunluk belgesi mevcuttur."
                ),
            }
        ]
    }
    code, ingest = api(
        "POST", f"/api/v1/organizations/{ORG}/capabilities/ingest", tok, ingest_body
    )
    if code != 200:
        log(f"[fail] ingest HTTP {code}: {ingest}")
        return 1
    caps = ingest.get("capabilityCount", 0)
    log(f"[ok] ingest capabilityCount={caps} documentId={evidence_id}")
    if caps < 1:
        log("[fail] expected at least 1 capability")
        return 1

    code, listed = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
    if code != 200 or not isinstance(listed, list):
        log(f"[fail] list capabilities HTTP {code}: {listed}")
        return 1
    log(f"[ok] list capabilities count={len(listed)}")

    fit_body = {
        "organizationId": ORG,
        "requirements": [
            {
                "id": str(uuid.uuid4()),
                "category": "SECURITY",
                "priority": "MUST",
                "text": "ISO 27001 belgesi sunulacaktır.",
            },
            {
                "id": str(uuid.uuid4()),
                "category": "TECHNICAL",
                "priority": "MUST",
                "text": "Sunucuda en az 16 DIMM bulunacaktır.",
            },
        ],
    }
    code, report = api(
        "POST", f"/api/v1/tenders/{tender_id}/company-fit", tok, fit_body
    )
    if code != 200:
        log(f"[fail] company-fit HTTP {code}: {report}")
        return 1
    overall = report.get("overall")
    must_met = report.get("mustMet")
    must_total = report.get("mustTotal")
    log(
        f"[ok] fit overall={overall} must={must_met}/{must_total} "
        f"score={report.get('overallScore')}"
    )
    if overall not in {"FIT", "CONDITIONAL"}:
        log(f"[fail] unexpected overall={overall}")
        return 1
    if int(must_met or 0) < 1:
        log("[fail] expected at least one MUST met")
        return 1

    code, latest = api("GET", f"/api/v1/tenders/{tender_id}/company-fit", tok)
    if code != 200 or not isinstance(latest, list) or len(latest) < 1:
        log(f"[fail] latest fit HTTP {code}: {latest}")
        return 1
    log(f"[ok] latest reports={len(latest)}")

    elapsed = round(time.time() - started, 2)
    log(f"[PASS] company-fit prod smoke in {elapsed}s")
    print(
        json.dumps(
            {
                "ok": True,
                "overall": overall,
                "mustMet": must_met,
                "mustTotal": must_total,
                "capabilityCount": caps,
                "elapsedSec": elapsed,
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
