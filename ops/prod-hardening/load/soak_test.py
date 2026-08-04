#!/usr/bin/env python3
"""Concurrent soak test against document-intelligence /v1/documents/parse.

Hard gates (defaults):
  success_rate >= 0.98
  digital p95 < 5s
  short_circuit_rate >= 0.90  (from result metadata.shortCircuited)
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

import urllib.error
import urllib.request


def http_json(method: str, url: str, body: dict | None = None, timeout: float = 60):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, (json.loads(raw) if raw else {})
    except TimeoutError:
        return 598, {"error": "client_timeout"}
    except urllib.error.URLError as error:
        return 599, {"error": str(error.reason)}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw}
        return error.code, payload


def upload_to_minio(local_pdf: Path, object_key: str) -> None:
    import boto3

    client = boto3.client(
        "s3",
        endpoint_url=os.environ.get("S3_ENDPOINT", "http://minio:9000"),
        aws_access_key_id=os.environ["MINIO_ACCESS_KEY"],
        aws_secret_access_key=os.environ["MINIO_SECRET_KEY"],
        region_name="us-east-1",
    )
    bucket = os.environ.get("S3_SOURCE_BUCKET", "specai-original")
    client.upload_file(str(local_pdf), bucket, object_key)


def run_one(endpoint: str, local_pdf: Path, org: str) -> dict[str, Any]:
    project_id = str(uuid.uuid4())
    document_id = str(uuid.uuid4())
    version_id = str(uuid.uuid4())
    object_key = (
        f"specai-original/{org}/{project_id}/{document_id}/{version_id}/soak.pdf"
    )
    upload_to_minio(local_pdf, object_key)
    started = time.perf_counter()
    status, submit = http_json(
        "POST",
        f"{endpoint.rstrip('/')}/v1/documents/parse",
        {
            "documentVersionId": version_id,
            "source": {
                "type": "S3_COMPATIBLE",
                "bucket": os.environ.get("S3_SOURCE_BUCKET", "specai-original"),
                "objectKey": object_key,
            },
            "mimeType": "application/pdf",
            "languageHint": "tr",
            "ocrMode": "AUTO",
            "extractTables": True,
            "extractImages": False,
            "correlationId": str(uuid.uuid4()),
        },
    )
    if status not in (200, 202):
        return {
            "ok": False,
            "error": f"submit_{status}",
            "durationSec": time.perf_counter() - started,
            "shortCircuited": False,
        }
    job_id = submit["jobId"]
    result_meta = None
    while True:
        code, job = http_json("GET", f"{endpoint.rstrip('/')}/v1/jobs/{job_id}")
        if code != 200:
            return {
                "ok": False,
                "error": f"status_{code}",
                "durationSec": time.perf_counter() - started,
                "shortCircuited": False,
            }
        state = job.get("status")
        if state in {"COMPLETED", "FAILED", "CANCELLED"}:
            if state == "COMPLETED":
                rcode, result = http_json(
                    "GET", f"{endpoint.rstrip('/')}/v1/jobs/{job_id}/result"
                )
                if rcode == 200:
                    result_meta = result
            break
        if time.perf_counter() - started > float(os.environ.get("SOAK_JOB_TIMEOUT_SEC", "120")):
            return {
                "ok": False,
                "error": "timeout",
                "durationSec": time.perf_counter() - started,
                "shortCircuited": False,
            }
        time.sleep(0.2)
    duration = time.perf_counter() - started
    short = bool(((result_meta or {}).get("metadata") or {}).get("shortCircuited"))
    return {
        "ok": state == "COMPLETED",
        "status": state,
        "durationSec": duration,
        "shortCircuited": short,
        "provider": (result_meta or {}).get("provider"),
        "pageCount": (result_meta or {}).get("pageCount"),
        "errorCode": job.get("errorCode"),
    }


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * pct
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    weight = rank - low
    return ordered[low] * (1 - weight) + ordered[high] * weight


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8090")
    parser.add_argument("--digital-pdf", type=Path, required=True)
    parser.add_argument("--concurrency", type=int, default=60)
    parser.add_argument("--jobs", type=int, default=120)
    parser.add_argument("--org", default="11111111-1111-1111-1111-111111111111")
    parser.add_argument("--min-success-rate", type=float, default=0.98)
    parser.add_argument("--max-p95-sec", type=float, default=5.0)
    parser.add_argument("--min-short-circuit-rate", type=float, default=0.90)
    parser.add_argument("--report", type=Path, default=Path("/tmp/soak-report.json"))
    args = parser.parse_args()

    if not args.digital_pdf.is_file():
        print("DIGITAL_PDF_MISSING", args.digital_pdf)
        return 2

    results: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [
            pool.submit(run_one, args.endpoint, args.digital_pdf, args.org)
            for _ in range(args.jobs)
        ]
        for future in as_completed(futures):
            results.append(future.result())

    ok = [item for item in results if item.get("ok")]
    durations = [float(item["durationSec"]) for item in ok]
    short = [item for item in ok if item.get("shortCircuited")]
    success_rate = len(ok) / max(1, len(results))
    short_rate = len(short) / max(1, len(ok))
    p95 = percentile(durations, 0.95)
    report = {
        "jobs": len(results),
        "successCount": len(ok),
        "successRate": round(success_rate, 4),
        "shortCircuitRate": round(short_rate, 4),
        "p50Sec": round(percentile(durations, 0.50), 4),
        "p95Sec": round(p95, 4),
        "p99Sec": round(percentile(durations, 0.99), 4),
        "meanSec": round(statistics.mean(durations), 4) if durations else None,
        "gates": {
            "successRate": success_rate >= args.min_success_rate,
            "p95": p95 < args.max_p95_sec,
            "shortCircuitRate": short_rate >= args.min_short_circuit_rate,
        },
        "passed": (
            success_rate >= args.min_success_rate
            and p95 < args.max_p95_sec
            and short_rate >= args.min_short_circuit_rate
        ),
        "failures": [item for item in results if not item.get("ok")][:20],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
