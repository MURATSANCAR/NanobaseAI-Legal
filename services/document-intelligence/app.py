from __future__ import annotations

import hashlib
import json
import os
import re
import sqlite3
import tempfile
import threading
import uuid
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor, TimeoutError
from pathlib import Path
from typing import Any, Literal

import boto3
from fastapi import BackgroundTasks, FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field


DATABASE_PATH = Path(os.getenv("JOB_DATABASE_PATH", "/var/lib/specai/jobs.sqlite3"))
TEMPORARY_DIRECTORY = Path(os.getenv("TEMPORARY_DIRECTORY", "/tmp/specai-docling"))
MAX_FILE_SIZE = int(os.getenv("MAX_FILE_SIZE_BYTES", str(100 * 1024 * 1024)))
MAX_PAGE_COUNT = int(os.getenv("MAX_PAGE_COUNT", "500"))
PROCESSING_TIMEOUT_SECONDS = int(os.getenv("PROCESSING_TIMEOUT_SECONDS", "900"))
ALLOWED_KEY = re.compile(
    r"^specai-original/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/"
    r"[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/[^/]+$"
)
SUPPORTED_MIME_TYPES = {
    "application/pdf": ".pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": ".docx",
}

executor = ThreadPoolExecutor(max_workers=int(os.getenv("WORKER_THREADS", "2")))
database_lock = threading.Lock()


class Source(BaseModel):
    type: Literal["S3_COMPATIBLE"]
    bucket: str = Field(min_length=1, max_length=100)
    objectKey: str = Field(min_length=1, max_length=1024)


class ParseRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentVersionId: uuid.UUID
    source: Source
    mimeType: str
    languageHint: str | None = Field(default=None, max_length=20)
    ocrMode: Literal["DISABLED", "AUTO", "FORCED"] = "AUTO"
    extractTables: bool = True
    extractImages: bool = False
    correlationId: uuid.UUID


class ParseResponse(BaseModel):
    jobId: uuid.UUID
    status: str


class JobResponse(BaseModel):
    jobId: uuid.UUID
    status: str
    currentStage: str
    progress: int = Field(ge=0, le=100)
    message: str
    errorCode: str | None = None


def connection() -> sqlite3.Connection:
    database = sqlite3.connect(DATABASE_PATH, timeout=30)
    database.row_factory = sqlite3.Row
    return database


def initialize_database() -> None:
    DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)
    TEMPORARY_DIRECTORY.mkdir(parents=True, exist_ok=True)
    with connection() as database:
        database.execute(
            """
            CREATE TABLE IF NOT EXISTS processing_job (
                id TEXT PRIMARY KEY,
                correlation_id TEXT NOT NULL UNIQUE,
                document_version_id TEXT NOT NULL,
                request_json TEXT NOT NULL,
                status TEXT NOT NULL,
                current_stage TEXT NOT NULL,
                progress INTEGER NOT NULL,
                message TEXT NOT NULL,
                error_code TEXT,
                result_json TEXT,
                cancel_requested INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """
        )


@asynccontextmanager
async def lifespan(_: FastAPI):
    initialize_database()
    yield
    executor.shutdown(wait=False, cancel_futures=True)


app = FastAPI(
    title="SpecAI Document Intelligence",
    version="1.0.0",
    lifespan=lifespan,
)


def s3_client():
    secrets_directory = Path(os.getenv("SECRETS_DIRECTORY", "/run/secrets"))
    access_key_file = secrets_directory / "MINIO_ACCESS_KEY"
    secret_key_file = secrets_directory / "MINIO_SECRET_KEY"
    access_key = os.getenv("S3_ACCESS_KEY") or (
        access_key_file.read_text(encoding="utf-8").strip()
        if access_key_file.is_file() else None
    )
    secret_key = os.getenv("S3_SECRET_KEY") or (
        secret_key_file.read_text(encoding="utf-8").strip()
        if secret_key_file.is_file() else None
    )
    if not access_key or not secret_key:
        raise RuntimeError("S3 credentials are unavailable")
    return boto3.client(
        "s3",
        endpoint_url=os.environ["S3_ENDPOINT"],
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name=os.getenv("S3_REGION", "us-east-1"),
    )


def update_job(
    job_id: str,
    *,
    status: str,
    stage: str,
    progress: int,
    message: str,
    error_code: str | None = None,
    result: dict[str, Any] | None = None,
) -> None:
    with database_lock, connection() as database:
        database.execute(
            """
            UPDATE processing_job
            SET status = ?, current_stage = ?, progress = ?, message = ?,
                error_code = ?, result_json = COALESCE(?, result_json),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (
                status,
                stage,
                progress,
                message[:500],
                error_code,
                json.dumps(result, ensure_ascii=False) if result is not None else None,
                job_id,
            ),
        )


def cancelled(job_id: str) -> bool:
    with connection() as database:
        row = database.execute(
            "SELECT cancel_requested FROM processing_job WHERE id = ?", (job_id,)
        ).fetchone()
    return bool(row and row["cancel_requested"])


@app.get("/health/live")
def health_live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready")
def health_ready() -> dict[str, str]:
    try:
        with connection() as database:
            database.execute("SELECT 1").fetchone()
        return {"status": "UP"}
    except sqlite3.Error as exception:
        raise HTTPException(status_code=503, detail="Job database is unavailable") from exception


@app.post("/v1/documents/parse", response_model=ParseResponse, status_code=202)
def parse_document(request: ParseRequest, background_tasks: BackgroundTasks) -> ParseResponse:
    if request.mimeType not in SUPPORTED_MIME_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported document media type")
    if not ALLOWED_KEY.fullmatch(request.source.objectKey):
        raise HTTPException(status_code=400, detail="Object key is outside the tenant prefix")
    expected_bucket = os.getenv("S3_SOURCE_BUCKET", "specai-original")
    if request.source.bucket != expected_bucket:
        raise HTTPException(status_code=400, detail="Source bucket is not allowed")
    with database_lock, connection() as database:
        existing = database.execute(
            "SELECT id, status FROM processing_job WHERE correlation_id = ?",
            (str(request.correlationId),),
        ).fetchone()
        if existing:
            return ParseResponse(jobId=uuid.UUID(existing["id"]), status=existing["status"])
        job_id = uuid.uuid4()
        database.execute(
            """
            INSERT INTO processing_job (
                id, correlation_id, document_version_id, request_json, status,
                current_stage, progress, message
            ) VALUES (?, ?, ?, ?, 'QUEUED', 'QUEUED', 5, ?)
            """,
            (
                str(job_id),
                str(request.correlationId),
                str(request.documentVersionId),
                request.model_dump_json(),
                "Document queued for parsing",
            ),
        )
    background_tasks.add_task(process_document, str(job_id), request)
    return ParseResponse(jobId=job_id, status="QUEUED")


@app.get("/v1/jobs/{job_id}", response_model=JobResponse)
def get_job(job_id: uuid.UUID) -> JobResponse:
    with connection() as database:
        row = database.execute(
            """
            SELECT id, status, current_stage, progress, message, error_code
            FROM processing_job WHERE id = ?
            """,
            (str(job_id),),
        ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Job was not found")
    return JobResponse(
        jobId=uuid.UUID(row["id"]),
        status=row["status"],
        currentStage=row["current_stage"],
        progress=row["progress"],
        message=row["message"],
        errorCode=row["error_code"],
    )


@app.get("/v1/jobs/{job_id}/result")
def get_result(job_id: uuid.UUID) -> dict[str, Any]:
    with connection() as database:
        row = database.execute(
            "SELECT status, result_json FROM processing_job WHERE id = ?",
            (str(job_id),),
        ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Job was not found")
    if row["status"] != "COMPLETED" or not row["result_json"]:
        raise HTTPException(status_code=409, detail="Job result is not ready")
    return json.loads(row["result_json"])


@app.post("/v1/jobs/{job_id}/cancel", status_code=202)
def cancel_job(job_id: uuid.UUID) -> dict[str, str]:
    with database_lock, connection() as database:
        changed = database.execute(
            """
            UPDATE processing_job
            SET cancel_requested = 1, status = 'CANCELLED',
                current_stage = 'CANCELLED', progress = 100,
                message = 'Document processing cancelled',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
            """,
            (str(job_id),),
        ).rowcount
    if changed == 0:
        with connection() as database:
            exists = database.execute(
                "SELECT 1 FROM processing_job WHERE id = ?", (str(job_id),)
            ).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="Job was not found")
    return {"status": "CANCELLED"}


def process_document(job_id: str, request: ParseRequest) -> None:
    suffix = SUPPORTED_MIME_TYPES[request.mimeType]
    local_path: Path | None = None
    try:
        if cancelled(job_id):
            return
        update_job(
            job_id,
            status="RUNNING",
            stage="PARSING",
            progress=15,
            message="Downloading source document",
        )
        client = s3_client()
        metadata = client.head_object(
            Bucket=request.source.bucket, Key=request.source.objectKey
        )
        size = int(metadata["ContentLength"])
        if size <= 0 or size > MAX_FILE_SIZE:
            raise SafeProcessingError("FILE_SIZE_LIMIT", "Source file exceeds the size limit")
        with tempfile.NamedTemporaryFile(
            dir=TEMPORARY_DIRECTORY, suffix=suffix, delete=False
        ) as temporary:
            local_path = Path(temporary.name)
        client.download_file(request.source.bucket, request.source.objectKey, str(local_path))
        if local_path.stat().st_size != size:
            raise SafeProcessingError("SOURCE_SIZE_MISMATCH", "Downloaded file size differs")
        if cancelled(job_id):
            return
        update_job(
            job_id,
            status="RUNNING",
            stage="PARSING",
            progress=35,
            message="Parsing document with Docling",
        )
        future = executor.submit(convert_with_docling, local_path, request)
        try:
            result = future.result(timeout=PROCESSING_TIMEOUT_SECONDS)
        except TimeoutError as exception:
            future.cancel()
            raise SafeProcessingError("PARSER_TIMEOUT", "Document parsing timed out") from exception
        if cancelled(job_id):
            return
        page_count = int(result["pageCount"])
        if page_count > MAX_PAGE_COUNT:
            raise SafeProcessingError("PAGE_COUNT_LIMIT", "Document exceeds the page limit")
        update_job(
            job_id,
            status="COMPLETED",
            stage="READY",
            progress=100,
            message="Document parsing completed",
            result=result,
        )
    except SafeProcessingError as exception:
        update_job(
            job_id,
            status="FAILED",
            stage="FAILED",
            progress=100,
            message=exception.safe_message,
            error_code=exception.code,
        )
    except Exception:
        update_job(
            job_id,
            status="FAILED",
            stage="FAILED",
            progress=100,
            message="Document parser failed",
            error_code="PARSER_FAILED",
        )
    finally:
        if local_path is not None:
            local_path.unlink(missing_ok=True)


class SafeProcessingError(Exception):
    def __init__(self, code: str, safe_message: str):
        super().__init__(safe_message)
        self.code = code
        self.safe_message = safe_message


def convert_with_docling(path: Path, request: ParseRequest) -> dict[str, Any]:
    from docling.datamodel.base_models import InputFormat
    from docling.datamodel.pipeline_options import PdfPipelineOptions
    from docling.document_converter import DocumentConverter, PdfFormatOption

    if request.mimeType == "application/pdf":
        options = PdfPipelineOptions()
        options.do_ocr = request.ocrMode != "DISABLED"
        options.do_table_structure = request.extractTables
        converter = DocumentConverter(
            format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)}
        )
    else:
        converter = DocumentConverter()
    converted = converter.convert(path)
    exported = converted.document.export_to_dict()
    return provider_neutral_result(exported, converted, request)


def provider_neutral_result(
    exported: dict[str, Any], converted: Any, request: ParseRequest
) -> dict[str, Any]:
    page_dimensions = extract_page_dimensions(exported)
    text_items = exported.get("texts") or []
    page_text: dict[int, list[str]] = {}
    parsed_items: list[dict[str, Any]] = []
    for index, item in enumerate(text_items):
        text = str(item.get("text") or "")
        provisions = item.get("prov") or []
        boxes = [
            normalized_box(provision, page_dimensions)
            for provision in provisions
            if provision.get("page_no")
        ]
        boxes = [box for box in boxes if box is not None]
        for provision in provisions:
            page_number = int(provision.get("page_no") or 1)
            page_text.setdefault(page_number, []).append(text)
        label = str(item.get("label") or "").lower()
        page_number = boxes[0]["page"] if boxes else 1
        parsed_items.append(
            {
                "index": index,
                "text": text,
                "normalized": normalize_text(text),
                "label": label,
                "boxes": boxes,
                "page": page_number,
            }
        )
    running_noise = detect_running_noise(parsed_items)
    clauses = build_reflowed_clauses(parsed_items, running_noise)
    page_numbers = sorted(page_dimensions) or sorted(page_text) or [1]
    pages: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    quality_values: list[float] = []
    for page_number in page_numbers:
        raw_text = "\n".join(page_text.get(page_number, []))
        quality = min(1.0, len(raw_text.strip()) / 500.0)
        quality_values.append(quality)
        if len(raw_text.strip()) < 25:
            warnings.append(
                {
                    "warningCode": "LOW_PAGE_TEXT_QUALITY",
                    "severity": "WARNING",
                    "message": "Page contains very little extractable text",
                    "pageNumber": page_number,
                    "metadata": {"characterCount": len(raw_text.strip())},
                }
            )
        width, height = page_dimensions.get(page_number, (1.0, 1.0))
        pages.append(
            {
                "pageNumber": page_number,
                "width": width,
                "height": height,
                "rotation": 0,
                "rawText": raw_text,
                "normalizedText": normalize_text(raw_text),
                "textQualityScore": quality,
                "thumbnailObjectKey": None,
                "metadata": {},
            }
        )
    tables = extract_tables(exported, page_dimensions) if request.extractTables else []
    language = request.languageHint or str(exported.get("language") or "und")
    quality = sum(quality_values) / len(quality_values) if quality_values else 0.0
    provider_version = getattr(converted, "version", None) or "docling"
    return {
        "documentVersionId": str(request.documentVersionId),
        "provider": "DOCLING",
        "providerVersion": str(provider_version),
        "pageCount": len(pages),
        "language": language,
        "textQualityScore": quality,
        "pages": pages,
        "clauses": clauses,
        "tables": tables,
        "warnings": warnings,
        "metadata": {
            "ocrMode": request.ocrMode,
            "extractTables": request.extractTables,
        },
    }


def extract_page_dimensions(exported: dict[str, Any]) -> dict[int, tuple[float, float]]:
    dimensions: dict[int, tuple[float, float]] = {}
    pages = exported.get("pages") or {}
    items = pages.items() if isinstance(pages, dict) else enumerate(pages, start=1)
    for key, page in items:
        page_number = int(page.get("page_no") or key)
        size = page.get("size") or {}
        dimensions[page_number] = (
            float(size.get("width") or 1.0),
            float(size.get("height") or 1.0),
        )
    return dimensions


def normalized_box(
    provision: dict[str, Any], dimensions: dict[int, tuple[float, float]]
) -> dict[str, Any] | None:
    page = int(provision.get("page_no") or 0)
    bbox = provision.get("bbox") or {}
    if page < 1 or not bbox:
        return None
    page_width, page_height = dimensions.get(page, (1.0, 1.0))
    left = float(bbox.get("l") or 0.0)
    right = float(bbox.get("r") or left)
    top = float(bbox.get("t") or 0.0)
    bottom = float(bbox.get("b") or top)
    if str(bbox.get("coord_origin") or "").upper().endswith("BOTTOMLEFT"):
        top, bottom = page_height - top, page_height - bottom
        top, bottom = min(top, bottom), max(top, bottom)
    x = clamp(left / page_width)
    y = clamp(top / page_height)
    width = clamp((right - left) / page_width)
    height = clamp((bottom - top) / page_height)
    width = min(width, 1.0 - x)
    height = min(height, 1.0 - y)
    return {"page": page, "x": x, "y": y, "width": width, "height": height}


def extract_tables(
    exported: dict[str, Any], dimensions: dict[int, tuple[float, float]]
) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    for item in exported.get("tables") or []:
        provisions = item.get("prov") or []
        boxes = [
            normalized_box(provision, dimensions)
            for provision in provisions
            if provision.get("page_no")
        ]
        boxes = [box for box in boxes if box is not None]
        page_numbers = [box["page"] for box in boxes] or [1]
        data = item.get("data") or {}
        markdown = str(item.get("text") or "")
        tables.append(
            {
                "pageStart": min(page_numbers),
                "pageEnd": max(page_numbers),
                "caption": item.get("caption"),
                "markdownContent": markdown,
                "structuredContent": data,
                "boundingBoxes": boxes,
                "contentHash": hashlib.sha256(
                    json.dumps(data, sort_keys=True).encode("utf-8")
                ).hexdigest(),
            }
        )
    return tables


HEADER_LABELS = frozenset({"title", "section_header", "chapter_header"})
FOOTER_NOISE = re.compile(
    r"(?i)^\s*(sayfa\s+\d+|page\s+\d+|©|\(c\)|tim\s*©)",
)


def detect_running_noise(items: list[dict[str, Any]]) -> set[str]:
    """Identify repeated running headers/footers that should not split sections."""
    counts: dict[str, int] = {}
    for item in items:
        normalized = item["normalized"]
        if not normalized or len(normalized) > 160:
            continue
        counts[normalized] = counts.get(normalized, 0) + 1
    noise = {
        text
        for text, count in counts.items()
        if count >= 3 or FOOTER_NOISE.search(text) is not None
    }
    for item in items:
        normalized = item["normalized"]
        if FOOTER_NOISE.search(normalized or ""):
            noise.add(normalized)
    return noise


def is_section_boundary(item: dict[str, Any], running_noise: set[str]) -> bool:
    if item["label"] not in HEADER_LABELS:
        return False
    text = (item["text"] or "").strip()
    if not text:
        return False
    normalized = item["normalized"]
    if normalized in running_noise:
        return False
    return True


def build_reflowed_clauses(
    items: list[dict[str, Any]], running_noise: set[str]
) -> list[dict[str, Any]]:
    """Attach body paragraphs to the preceding real heading until the next heading.

    Docling often emits heading-only clauses while obligation text lives on pages.
    Reflow keeps title as the heading and concatenates following non-header text so
    requirement extraction / grounding see the real şartname body.
    """
    clauses: list[dict[str, Any]] = []
    index = 0
    while index < len(items):
        item = items[index]
        if not is_section_boundary(item, running_noise):
            index += 1
            continue
        title = item["text"].strip()
        body_parts = [title]
        boxes = list(item["boxes"])
        pages = [item["page"]]
        source_indexes = [item["index"]]
        cursor = index + 1
        while cursor < len(items):
            nxt = items[cursor]
            if is_section_boundary(nxt, running_noise):
                break
            nxt_text = (nxt["text"] or "").strip()
            nxt_norm = nxt["normalized"]
            if nxt_text and nxt_norm not in running_noise:
                body_parts.append(nxt_text)
                boxes.extend(nxt["boxes"])
                pages.append(nxt["page"])
                source_indexes.append(nxt["index"])
            cursor += 1
        raw_text = "\n".join(body_parts)
        page_start = min(pages) if pages else item["page"]
        page_end = max(pages) if pages else item["page"]
        clauses.append(
            {
                "sourceId": f"docling-{item['index']}",
                "parentSourceId": None,
                "clauseNumber": extract_clause_number(title),
                "title": title[:500],
                "rawText": raw_text,
                "normalizedText": normalize_text(raw_text),
                "clauseType": item["label"].upper(),
                "pageStart": page_start,
                "pageEnd": page_end,
                "boundingBoxes": boxes,
                "contentHash": hashlib.sha256(raw_text.encode("utf-8")).hexdigest(),
                "sortOrder": item["index"],
                "metadata": {
                    "providerLabel": item["label"],
                    "providerItem": item["index"],
                    "reflowed": True,
                    "reflowedItemCount": len(source_indexes),
                    "sourceItemIndexes": source_indexes,
                },
            }
        )
        index = cursor
    return clauses


def extract_clause_number(text: str) -> str | None:
    match = re.match(r"^\s*(\d+(?:\.\d+)*)[\s.)-]+", text)
    return match.group(1) if match else None


def normalize_text(value: str) -> str:
    return " ".join(value.split())


def clamp(value: float) -> float:
    return max(0.0, min(1.0, value))
