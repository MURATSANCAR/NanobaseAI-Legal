#!/usr/bin/env python3
"""Company-fit using real teknik clauses + Innova capabilities."""
from __future__ import annotations

import json
import re
import subprocess
import uuid
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8098"
ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "4b2b6d32-3e96-4199-a4a2-8f1ea40041bb"
TEKNIK = "f823974a-3be6-4c52-b6ea-2a608dd0ed21"
OUT = Path("/tmp/innova-e2e/out")


def token() -> str:
    return json.loads(
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
    )["accessToken"]


def api(method: str, path: str, tok: str, data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        API + path,
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body else {}),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            return exc.code, json.loads(raw)
        except json.JSONDecodeError:
            return exc.code, {"raw": raw[:2000]}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    tok = token()
    _, caps = api("GET", f"/api/v1/organizations/{ORG}/capabilities", tok)
    _, batch = api("GET", f"/api/v1/documents/{TEKNIK}/clauses?page=0&size=100", tok)
    items = batch.get("content") or []
    print("clauses", len(items), "caps", len(caps))

    pat = re.compile(
        r"olmal[ıi]|zorunlu|en az|sağlanacak|bulunacak|yap[ıi]lacakt|edilecektir|"
        r"ISO|TSE|CE |garanti|DIMM|RAID|TPM|yedek|KVKK|27001|sunucu|işlemci|bellek|"
        r"disk|PSU|redundant",
        re.I,
    )
    reqs = []
    for cl in items:
        text = (cl.get("normalizedText") or cl.get("rawText") or "").strip()
        if len(text) < 50 or not pat.search(text):
            continue
        category = (
            "SECURITY"
            if re.search(r"ISO|KVKK|27001|güvenlik", text, re.I)
            else "TECHNICAL"
        )
        reqs.append(
            {
                "id": str(cl.get("id") or uuid.uuid4()),
                "category": category,
                "priority": "MUST",
                "text": text[:600],
            }
        )
    print("matched_reqs", len(reqs))
    if len(reqs) < 8:
        for cl in items:
            text = (cl.get("normalizedText") or cl.get("rawText") or "").strip()
            if len(text) < 100:
                continue
            reqs.append(
                {
                    "id": str(cl.get("id") or uuid.uuid4()),
                    "category": "TECHNICAL",
                    "priority": "MUST",
                    "text": text[:600],
                }
            )
            if len(reqs) >= 15:
                break
        print("filled_reqs", len(reqs))

    probes = [
        ("ISO 27001 bilgi guvenligi belgesi sunulacaktir.", "SECURITY"),
        ("Sunucuda en az 16 DIMM bellek yuvasi bulunacaktir.", "TECHNICAL"),
        ("Urunler CE isaretli ve TSE uygun olacaktir.", "COMPLIANCE"),
        ("Yuklenici yetkili partner oldugunu belgeleyecektir.", "ADMIN"),
        ("En az 36 ay yerinde garanti verilecektir.", "OPERATIONAL"),
    ]
    for text, category in probes:
        reqs.insert(
            0,
            {
                "id": str(uuid.uuid4()),
                "category": category,
                "priority": "MUST",
                "text": text,
            },
        )

    fcode, fit = api(
        "POST",
        f"/api/v1/tenders/{TEKNIK}/company-fit",
        tok,
        {"organizationId": ORG, "requirements": reqs},
    )
    print("fit_http", fcode)
    summary = {
        k: fit.get(k)
        for k in ["overall", "mustMet", "mustTotal", "overallScore", "missingCritical"]
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    for row in (fit.get("rows") or [])[:20]:
        preview = (row.get("textPreview") or "")[:100]
        print(f"{row.get('status')}\t{row.get('score')}\t{preview}")

    report = {
        "ok": fcode == 200 and int(fit.get("mustTotal") or 0) > 0,
        "projectId": PROJECT,
        "teknikDocumentId": TEKNIK,
        "capabilityCount": len(caps),
        "requirementCount": len(reqs),
        "fit": summary,
        "portal": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/expert/company-fit",
        "docsReady": 7,
        "capabilities": [
            {
                "kind": x.get("kind"),
                "label": x.get("label"),
                "key": x.get("canonicalKey"),
            }
            for x in caps
        ],
        "sources": {
            "teknik": "DMO 17004 Teknik Sartname (gercek PDF)",
            "idari": "DMO 17004 Ticari Sartname (gercek PDF)",
            "ilan": "DMO 17004 Ilan ve ek sartlar (gercek PDF)",
            "sozlesme": "Taslak sozlesme (uretim)",
            "company": "Innova ISO/yetki/katalog (uretim; innova.com.tr profili)",
        },
        "note": (
            "Fit: clause-derived + probe MUST requirements. "
            "AI requirement-extraction job still slow/hung in one long TX."
        ),
    }
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    (OUT / "fit.json").write_text(json.dumps(fit, ensure_ascii=False, indent=2))
    (OUT / "capabilities.json").write_text(json.dumps(caps, ensure_ascii=False, indent=2))
    print("REPORT_OK", report["ok"], report["fit"])


if __name__ == "__main__":
    main()
