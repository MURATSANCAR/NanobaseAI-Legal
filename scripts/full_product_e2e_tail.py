#!/usr/bin/env python3
"""Finish remaining E2E steps after compliance COMPLETED."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8098"
PROJECT = "0886a1f0-05e1-4278-ae65-03a06b9d07f2"
DOC = "881e54d8-98d9-4ab7-82d1-601134404422"
JOB = "6a2c5215-0cf3-409b-af3e-b462fb82dabd"
EVAL = "75bf6020-eb9b-4df3-912a-cba5c0629347"
EVALUATION_REVIEWED = "50000000-0000-0000-0000-00000000001c"
PARTIALLY = "50000000-0000-0000-0000-00000000000c"
CUSTOM_QUERY_SECTION = "70000000-0000-0000-0000-000000000041"
PDF_FORMAT = "70000000-0000-0000-0000-000000000038"
DATA_POLICY = "70000000-0000-0000-0000-000000000101"
TEMPLATE = "70000000-0000-0000-0000-000000000111"

REPORT = {
    "ok": True,
    "projectId": PROJECT,
    "documentId": DOC,
    "complianceJobId": JOB,
    "sourceDocument": (
        "https://cdniys.tarimorman.gov.tr/api/File/GetGaleriFile/425/DosyaGaleri/622/"
        "sulama_sebekesinde_otomasyon_genel_teknik_sartnamesi_r00_20250520.pdf"
    ),
    "steps": [],
}


def log(msg: str) -> None:
    print(msg, flush=True)


def step(name: str, **payload) -> None:
    entry = {"step": name, **payload}
    REPORT["steps"].append(entry)
    log(f"[STEP] {name} {json.dumps(payload, ensure_ascii=False)[:700]}")


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


def api(method: str, path: str, tok: str, data=None, timeout: int = 180):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(
        f"{API}{path}",
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body else {}),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:2000]}
        return exc.code, parsed


def countish(payload):
    if isinstance(payload, list):
        return len(payload)
    if isinstance(payload, dict):
        if isinstance(payload.get("content"), list):
            return payload.get("totalElements", len(payload["content"]))
        return payload.get("totalElements")
    return None


def page_items(payload):
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict) and isinstance(payload.get("content"), list):
        return payload["content"]
    return []


def main() -> int:
    tok = token()
    _rscode, risks = api("GET", f"/api/v1/tenders/{PROJECT}/risks", tok)
    _cfcode, conflicts = api("GET", f"/api/v1/tenders/{PROJECT}/conflicts", tok)
    step("12_risks_conflicts", risks=countish(risks), conflicts=countish(conflicts))

    # evidence on sample eval
    _ecode, detail = api("GET", f"/api/v1/compliance-evaluations/{EVAL}", tok)
    step(
        "11b_eval_detail",
        http=_ecode,
        suggested=detail.get("suggestedDecision") or detail.get("suggestedDecisionCode"),
        evidenceCount=detail.get("evidenceCount"),
        grounding=detail.get("groundingStatus") or detail.get("groundingStatusCode"),
    )

    rvcode, reviewed = api(
        "POST",
        f"/api/v1/compliance-evaluations/{EVAL}/review",
        tok,
        {
            "finalDecisionConceptId": PARTIALLY,
            "changeTypeConceptId": EVALUATION_REVIEWED,
            "feedbackTypeConceptId": None,
            "reason": "E2E canlı doğrulama: kullanıcı onay/düzeltme",
        },
    )
    step("13_review", http=rvcode, evaluationId=EVAL, ok=rvcode < 300, detail=str(reviewed)[:200])

    stamp = int(time.time())
    _dcode2, definition = api(
        "POST",
        "/api/v1/report-definitions",
        tok,
        {
            "reportCode": f"E2E_COMPLIANCE_{stamp}",
            "name": "E2E Compliance Snapshot",
            "description": "Uçtan uca canlı rapor",
            "scope": "ORGANIZATION",
        },
    )
    def_id = definition.get("id")
    _vcode, version = api(
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
                    "titleTemplate": "Doğrulanmış proje özeti",
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
        f"/api/v1/tenders/{PROJECT}/reports",
        tok,
        {
            "reportDefinitionVersionId": version_id,
            "formatConceptIds": [PDF_FORMAT],
            "staleOverrideApprovalId": None,
        },
        timeout=300,
    )
    artifacts = report_job.get("artifacts") or []
    step(
        "14_report",
        http=gcode,
        status=report_job.get("statusConceptCode"),
        artifacts=len(artifacts),
        error=report_job.get("errorMessage"),
        jobId=report_job.get("id"),
    )
    if artifacts:
        art_id = artifacts[0]["id"]
        _ucode, url_body = api("GET", f"/api/v1/report-artifacts/{art_id}/download-url", tok)
        url = url_body.get("url")
        download_path = f"/tmp/e2e_report_{PROJECT}.pdf"
        if url:
            subprocess.check_call(["curl", "-fsSL", "-o", download_path, url])
            step("15_download", path=download_path, bytes=Path(download_path).stat().st_size)
        else:
            REPORT["ok"] = False
            step("15_download", error=url_body)
    else:
        REPORT["ok"] = False
        step("15_download", skipped=True, detail=str(report_job)[:400])

    _acode, audit = api("GET", "/api/v1/audit-events?page=0&size=30&sort=createdAt,desc", tok)
    items = page_items(audit)
    step(
        "16_audit",
        count=countish(audit),
        sample=[
            {
                "eventType": item.get("eventType"),
                "entityType": item.get("entityType"),
                "createdAt": item.get("createdAt"),
            }
            for item in items[:15]
        ],
    )

    # pull evals summary
    _ecode2, evals = api("GET", f"/api/v1/tenders/{PROJECT}/compliance-evaluations", tok)
    step("11_results_summary", evalCount=countish(evals))

    REPORT["portal"] = {
        "documents": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/documents",
        "requirements": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/requirements",
        "compliance": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/analysis/compliance",
        "activity": f"https://portal.nanobase.ai/legal/#/project/{PROJECT}/project/activity",
    }
    early = {}
    early_path = Path("/tmp/full_product_e2e_early.json")
    if early_path.exists():
        early = json.loads(early_path.read_text(encoding="utf-8"))
    merged = {
        **early,
        **REPORT,
        "steps": (early.get("steps") or []) + REPORT["steps"],
    }
    Path("/tmp/full_product_e2e_report.json").write_text(
        json.dumps(merged, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    log(f"DONE ok={REPORT['ok']}")
    return 0 if REPORT["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
