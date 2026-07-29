#!/usr/bin/env python3
"""Server-side Legal E2E: wait for LLM → extract → knowledge → compliance (seed fallback)."""

from __future__ import annotations

import hashlib
import json
import subprocess
import time
import uuid
import urllib.error
import urllib.request

API = "http://127.0.0.1:8098"
LLM = "http://127.0.0.1:8010"
ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
TIM_DOC = "0069a971-928c-49e0-90b0-71678332df5f"
TIM_VER = "5c545250-d928-407c-bc15-5b9aeba7ccdf"
ISO_DOC = "be3108eb-a38b-411d-8d1a-7a660c3efb8a"
ISO_VER = "aa0267ac-868a-40c1-ad17-2370b6022a03"
SLA_DOC = "b9be9d34-c733-490d-bf89-02f9eb812aab"
SLA_VER = "e7d587ae-2bc2-42e6-8423-2435b13c81d1"
JOB = "109e3dfe-233d-4861-a13a-68ade7d8dc10"
PROFILE = "b3b8e959-78eb-441a-a52c-35c14370c18e"
ONTOLOGY = "40000000-0000-0000-0000-000000000002"
POLICY = "40000000-0000-0000-0000-000000000043"
PROMPT = "40000000-0000-0000-0000-000000000033"
COMPANY = "814ddcdb-1416-4da2-930d-b70405fcced9"
CERT_TYPE = "50000000-0000-0000-0000-0000000000a2"
CAP_CONCEPT = "50000000-0000-0000-0000-000000000004"
CLAUSE_TIER = "af56cc79-8647-4de5-a0d0-e867720496fa"
CLAUSE_SLA = "676675a7-f236-4734-a8a9-372d10c4ce0c"
CLAUSE_FKM = "4b0da506-9dd3-4660-bd67-b172e812cf5e"
SLA_CLAUSE = "5aabe814-9a54-4dab-859c-1a36034ca8c1"
ISO_CLAUSE = "24961b17-48a2-49b9-bded-787dbd116f6a"

TERMINAL = {"COMPLETED", "FAILED", "CANCELLED"}


def log(msg: str) -> None:
    print(msg, flush=True)


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


def api(method: str, url: str, tok: str, data=None, timeout: int = 180):
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
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read()
        return json.loads(raw) if raw else {}


def llm_busy() -> bool:
    try:
        with urllib.request.urlopen(f"{LLM}/slots", timeout=5) as r:
            slots = json.load(r)
        if isinstance(slots, list) and slots:
            return bool(slots[0].get("is_processing"))
    except Exception as exc:  # noqa: BLE001
        log(f"llm_slots_err={exc}")
    return True


def wait_llm(max_wait: int = 1800) -> bool:
    start = time.time()
    while time.time() - start < max_wait:
        busy = llm_busy()
        log(f"[llm] busy={busy} elapsed={int(time.time() - start)}s")
        if not busy:
            # quick ping
            try:
                payload = json.dumps(
                    {
                        "model": "nanobase-qwen36-35b-a3b-mtp",
                        "messages": [{"role": "user", "content": "Reply with OK only"}],
                        "max_tokens": 8,
                        "temperature": 0,
                    }
                ).encode()
                req = urllib.request.Request(
                    f"{LLM}/v1/chat/completions",
                    data=payload,
                    method="POST",
                    headers={"Content-Type": "application/json"},
                )
                with urllib.request.urlopen(req, timeout=90) as r:
                    body = json.loads(r.read().decode())
                text = (
                    ((body.get("choices") or [{}])[0].get("message") or {}).get("content")
                    or ""
                )
                log(f"[llm] ping_ok content={text!r}")
                return True
            except Exception as exc:  # noqa: BLE001
                log(f"[llm] ping_failed={exc}")
        time.sleep(15)
    return False


def db_creds():
    user = subprocess.check_output(
        ["bash", "-lc", "sudo grep DATABASE_USER /etc/nanobaseai/legal.env | cut -d= -f2"],
        text=True,
    ).strip()
    pw = subprocess.check_output(
        ["bash", "-lc", "sudo grep DATABASE_PASSWORD /etc/nanobaseai/legal.env | cut -d= -f2"],
        text=True,
    ).strip()
    return user, pw


def psql(sql: str) -> str:
    user, pw = db_creds()
    return subprocess.check_output(
        [
            "docker",
            "exec",
            "-e",
            f"PGPASSWORD={pw}",
            "actenora-prodlike-postgres",
            "psql",
            "-U",
            user,
            "-d",
            "specai",
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            sql,
        ],
        text=True,
    )


def ensure_docs_ready(tok: str) -> None:
    for doc_id, label in [(TIM_DOC, "TIM"), (ISO_DOC, "ISO"), (SLA_DOC, "SLA")]:
        doc = api("GET", f"{API}/api/v1/documents/{doc_id}", tok)
        status = doc.get("status") or (doc.get("currentVersion") or {}).get("processingStatus")
        log(f"[doc] {label} status={status}")
        if status != "READY":
            raise SystemExit(f"document {label} not READY: {status}")


def poll_job(tok: str, url: str, label: str, max_wait: int = 1200):
    start = time.time()
    last = {}
    while time.time() - start < max_wait:
        try:
            last = api("GET", url, tok)
        except Exception as exc:  # noqa: BLE001
            log(f"[{label}] poll_err={exc}")
            tok = token()
            time.sleep(10)
            continue
        status = last.get("status")
        extra = (
            last.get("errorCode")
            or last.get("error_code")
            or last.get("extractedRequirementCount")
            or last.get("extractedEntityCount")
            or last.get("errorMessage")
            or last.get("error_message")
        )
        log(f"[{label}] {status} extra={extra} t={int(time.time() - start)}s")
        if status in TERMINAL:
            return last, tok
        time.sleep(15)
    return last, tok


def seed_analyst(tok: str) -> None:
    """Reuse the proven server seed script when AI yields empty extracts."""
    log("[seed] invoking /tmp/e2e_seed_and_compliance.py (seed+compliance inside)")
    # Prefer only the seed half: run Python module that stops before compliance if patched.
    # Fallback: call existing script; it ends with compliance which is fine if we skip later.
    subprocess.check_call(["python3", "/tmp/e2e_seed_and_compliance.py"])


def run_compliance(tok: str):
    log("[compliance] starting analysis")
    job = api("POST", f"{API}/api/v1/tenders/{PROJECT}/compliance-analyses", tok)
    log(f"[compliance] job={job}")
    jid = job.get("id")
    if not jid:
        raise SystemExit(f"no compliance job id: {job}")
    result, tok = poll_job(
        tok, f"{API}/api/v1/compliance-analyses/{jid}", "compliance", max_wait=900
    )
    evals = api("GET", f"{API}/api/v1/tenders/{PROJECT}/compliance-evaluations", tok)
    summary = {"job": result, "evals": evals}
    open("/tmp/legal-e2e-server-run.json", "w").write(json.dumps(summary, indent=2, default=str))
    if isinstance(evals, list):
        decisions = {}
        for row in evals:
            d = row.get("suggestedDecision") or row.get("decision") or "?"
            decisions[d] = decisions.get(d, 0) + 1
        log(f"[compliance] decisions={decisions} total={len(evals)}")
    else:
        log(f"[compliance] evals={evals}")
    return summary, tok


def main() -> None:
    log("=== Legal server E2E start ===")
    tok = token()
    ensure_docs_ready(tok)

    llm_ok = wait_llm(max_wait=2400)
    log(f"[llm] available={llm_ok}")

    req_job = None
    kn_jobs = []
    used_seed = False

    req_result = {}
    if llm_ok:
        log("[ai] requirement extraction TIM (serial)")
        req_job = api(
            "POST",
            f"{API}/api/v1/documents/{TIM_DOC}/requirement-extractions",
            tok,
            {},
        )
        log(f"[ai] req_job={req_job}")
        req_result, tok = poll_job(
            tok,
            f"{API}/api/v1/requirement-extractions/{req_job.get('id')}",
            "requirement",
            max_wait=1500,
        )

        for doc_id, label in [(ISO_DOC, "knowledge_iso"), (SLA_DOC, "knowledge_sla")]:
            wait_llm(max_wait=900)
            j = api("POST", f"{API}/api/v1/documents/{doc_id}/knowledge-extractions", tok, {})
            log(f"[ai] {label}={j}")
            res, tok = poll_job(
                tok,
                f"{API}/api/v1/knowledge-extractions/{j.get('id')}",
                label,
                max_wait=1500,
            )
            kn_jobs.append(res)

    reqs = api("GET", f"{API}/api/v1/tenders/{PROJECT}/requirements?size=50", tok)
    count = reqs.get("totalElements") or 0
    log(f"[state] requirement_count={count} llm_ok={llm_ok} req_status={(req_result or {}).get('status')}")
    if count == 0:
        used_seed = True
        seed_analyst(tok)
        # seed script already ran compliance; still re-run for a fresh job after AI path
        tok = token()

    summary, tok = run_compliance(tok)
    out = {
        "llmOk": llm_ok,
        "usedSeed": used_seed,
        "requirementJob": req_job,
        "knowledgeJobs": kn_jobs,
        "compliance": summary.get("job"),
        "portal": f"https://portal.nanobase.ai/legal/tenders/{PROJECT}",
    }
    open("/tmp/legal-e2e-server-summary.json", "w").write(json.dumps(out, indent=2, default=str))
    log("=== DONE ===")
    log(json.dumps(out, indent=2, default=str))


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        log(f"HTTPError {exc.code}: {body[:2000]}")
        raise
