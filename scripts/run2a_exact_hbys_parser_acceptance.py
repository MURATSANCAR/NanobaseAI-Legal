#!/usr/bin/env python3
"""RUN_2A exact HBYS parser acceptance — parser-only, no manual seeds."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import time
import uuid
from pathlib import Path

import boto3
import urllib.error
import urllib.request

API = os.environ.get("SPECAI_API", "http://127.0.0.1:8098")
DI = os.environ.get("DOCLING_BASE_URL", "http://127.0.0.1:8090")
ORG = "11111111-1111-1111-1111-111111111111"
EXPECTED_SHA = "af9fae5be4d7d0f24de4c8953c8590b0cf036fa772c502d35e5e539304ac1486"
SOURCE = Path(
    os.environ.get(
        "HBYS_PDF",
        "/tmp/nanobase-hbys-public-e2e/source/hbys-technical-specification.pdf",
    )
)
OUT = Path(
    os.environ.get(
        "RUN2A_OUT",
        "/tmp/nanobase-hbys-public-e2e/run-2a-diagnostic",
    )
)
# True page count from pdfinfo/pypdf (file(1) falsely said 20).
EXPECTED_PAGES = int(os.environ.get("HBYS_EXPECTED_PAGES", "235"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def http_json(method: str, url: str, body: dict | None = None, headers: dict | None = None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw}
        return error.code, payload


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    if not SOURCE.is_file():
        print("SOURCE_MISSING", SOURCE)
        return 2
    digest = sha256(SOURCE)
    if digest != EXPECTED_SHA:
        print("HASH_MISMATCH", digest)
        return 2

    # Upload to MinIO under org prefix for DI parse request.
    endpoint = os.environ.get("S3_ENDPOINT", "http://127.0.0.1:9000")
    access = os.environ["MINIO_ACCESS_KEY"]
    secret = os.environ["MINIO_SECRET_KEY"]
    bucket = os.environ.get("S3_SOURCE_BUCKET", "specai-original")
    document_version_id = str(uuid.uuid4())
    project_id = str(uuid.uuid4())
    document_id = str(uuid.uuid4())
    object_key = (
        f"specai-original/{ORG}/{project_id}/{document_id}/"
        f"{document_version_id}/hbys-technical-specification.pdf"
    )
    client = boto3.client(
        "s3",
        endpoint_url=endpoint,
        aws_access_key_id=access,
        aws_secret_access_key=secret,
        region_name="us-east-1",
    )
    client.upload_file(str(SOURCE), bucket, object_key)

    correlation_id = str(uuid.uuid4())
    status, submit = http_json(
        "POST",
        f"{DI}/v1/documents/parse",
        {
            "documentVersionId": document_version_id,
            "source": {
                "type": "S3_COMPATIBLE",
                "bucket": bucket,
                "objectKey": object_key,
            },
            "mimeType": "application/pdf",
            "languageHint": "tr",
            "ocrMode": "AUTO",
            "extractTables": True,
            "extractImages": False,
            "correlationId": correlation_id,
        },
    )
    if status != 202:
        print("SUBMIT_FAILED", status, submit)
        return 1
    job_id = submit["jobId"]
    started = time.time()
    result = None
    last = None
    while True:
        code, last = http_json("GET", f"{DI}/v1/jobs/{job_id}")
        if code != 200:
            print("STATUS_FAILED", code, last)
            return 1
        print(
            json.dumps(
                {
                    "jobId": job_id,
                    "status": last.get("status"),
                    "stage": last.get("currentStage"),
                    "progress": last.get("progress"),
                    "message": last.get("message"),
                    "elapsedSec": round(time.time() - started, 1),
                },
                ensure_ascii=False,
            ),
            flush=True,
        )
        if last.get("status") in {"COMPLETED", "FAILED", "CANCELLED"}:
            break
        if time.time() - started > int(os.environ.get("ACCEPTANCE_TIMEOUT_SEC", "45000")):
            print("ACCEPTANCE_WALL_TIMEOUT")
            return 1
        time.sleep(15)

    duration = round(time.time() - started, 1)
    if last.get("status") != "COMPLETED":
        payload = {
            "parserJobId": job_id,
            "status": last.get("status"),
            "errorCode": last.get("errorCode"),
            "message": last.get("message"),
            "durationSec": duration,
            "acceptance": "FAIL",
        }
        (OUT / "parser-result.json").write_text(
            json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        print("PARSER_FAILED", payload)
        return 1

    code, result = http_json("GET", f"{DI}/v1/jobs/{job_id}/result")
    if code != 200:
        print("RESULT_FAILED", code, result)
        return 1

    metadata = result.get("metadata") or {}
    plan = metadata.get("parserPlan") or {}
    page_count = int(result.get("pageCount") or 0)
    processed = int(metadata.get("processedPageCount") or page_count)
    failed = int(metadata.get("failedPageCount") or 0)
    layout_blocks = int(metadata.get("layoutBlockCount") or 0)
    terminal = metadata.get("terminalStatus") or last.get("currentStage")
    quality = metadata.get("qualityGate")
    ok = (
        digest == EXPECTED_SHA
        and page_count == EXPECTED_PAGES
        and processed == EXPECTED_PAGES
        and failed == 0
        and layout_blocks > 0
        and terminal == "READY"
        and quality == "PASS"
    )
    payload = {
        "parserJobId": job_id,
        "sourceSha256": digest,
        "pageCount": page_count,
        "processedPageCount": processed,
        "failedPageCount": failed,
        "layoutBlockCount": layout_blocks,
        "terminalStatus": terminal,
        "qualityGate": quality,
        "durationSec": duration,
        "processingPlan": plan,
        "providerPerPage": plan.get("providerPerPage"),
        "ocrPages": plan.get("ocrPages"),
        "doclingPages": plan.get("doclingPages"),
        "nativeTextPages": plan.get("nativeTextPages"),
        "fallbackPages": plan.get("fallbackPages"),
        "checkpointCount": metadata.get("checkpointCount") or plan.get("checkpointCount"),
        "slowestBatch": plan.get("slowestBatch"),
        "acceptance": "PASS" if ok else "FAIL",
        "manualSeed": 0,
    }
    (OUT / "parser-result.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    checkpoints = {
        "checkpointCount": payload["checkpointCount"],
        "jobId": job_id,
    }
    (OUT / "page-checkpoints.json").write_text(
        json.dumps(checkpoints, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"acceptance": payload["acceptance"], **payload}, ensure_ascii=False))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
