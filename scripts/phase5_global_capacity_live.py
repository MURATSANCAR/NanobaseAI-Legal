#!/usr/bin/env python3
"""Phase 5: global capacity across two orchestrator HTTP endpoints (capacity=1)."""
from __future__ import annotations

import json
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ORCH_A = "http://127.0.0.1:8095"
ORCH_B = "http://127.0.0.1:8096"
PROFILE = "BALANCED"
REPORT = Path("/tmp/phase5_global_capacity_report.json")

# Minimal schema/body — we only care about capacity acquire race before/during model.
BODY = {
    "model": "nanobase-spec-ai",
    "profile": PROFILE,
    "promptComponents": ["Return valid JSON only."],
    "outputSchema": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "decision": {"type": "string"},
            "summary": {"type": "string"},
        },
        "required": ["decision", "summary"],
    },
    "context": {"probe": "phase5-capacity"},
    "maximumOutputTokens": 64,
}


def get(url: str, timeout: float = 5.0):
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read())


def post_extract(base: str, correlation_id: str, timeout: float = 30.0):
    data = json.dumps(BODY).encode()
    req = urllib.request.Request(
        base + "/v1/extractions",
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Correlation-ID": correlation_id,
        },
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return {
                "base": base,
                "status": resp.status,
                "body": json.loads(resp.read()),
                "elapsedMs": int((time.time() - started) * 1000),
                "errorCode": None,
            }
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except Exception:
            parsed = {"raw": raw}
        detail = parsed.get("detail") if isinstance(parsed, dict) else parsed
        code = None
        if isinstance(detail, dict):
            code = detail.get("code")
        return {
            "base": base,
            "status": exc.code,
            "body": parsed,
            "elapsedMs": int((time.time() - started) * 1000),
            "errorCode": code,
        }


def main() -> int:
    ready_a = get(ORCH_A + "/health/ready")
    ready_b = get(ORCH_B + "/health/ready")
    snap0 = get(ORCH_A + f"/v1/capacity/{PROFILE}/snapshot")
    barrier = threading.Barrier(2)
    results = []

    def run(base: str):
        barrier.wait(timeout=30)
        return post_extract(base, str(uuid.uuid4()), timeout=20)

    with ThreadPoolExecutor(max_workers=2) as pool:
        futs = [pool.submit(run, ORCH_A), pool.submit(run, ORCH_B)]
        for fut in as_completed(futs):
            results.append(fut.result())

    # One should acquire; the other should be CAPACITY_FULL / WAIT_TIMEOUT / still waiting.
    # With wait timeout short for probe we use deployment default — may be long.
    # Prefer snapshot peak during race.
    time.sleep(0.5)
    snap_mid = get(ORCH_A + f"/v1/capacity/{PROFILE}/snapshot")
    # Wait for both to finish or timeout already returned
    time.sleep(2)
    snap_end = get(ORCH_A + f"/v1/capacity/{PROFILE}/snapshot")

    codes = {r.get("errorCode") for r in results}
    statuses = {r.get("status") for r in results}
    acquired = sum(1 for r in results if r.get("status") == 200)
    rejected = sum(
        1
        for r in results
        if r.get("errorCode") in {"CAPACITY_FULL", "CAPACITY_WAIT_TIMEOUT", "LLM_QUEUE_TIMEOUT", "LLM_OVERLOADED"}
    )
    peak = snap_mid.get("active")
    passed = peak is not None and int(peak) <= 1 and acquired <= 1

    report = {
        "readyA": ready_a,
        "readyB": ready_b,
        "snap0": snap0,
        "snapMid": snap_mid,
        "snapEnd": snap_end,
        "results": results,
        "acquired": acquired,
        "rejected": rejected,
        "codes": sorted(x for x in codes if x),
        "statuses": sorted(statuses),
        "modelConcurrencyPeak": peak,
        "result": "PASS" if passed else "FAIL",
    }
    REPORT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
