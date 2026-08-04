"""Bounded, checkpointed document parsing orchestration."""

from __future__ import annotations

import hashlib
import os
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from page_capability import PageCapability, build_batches
from parser_checkpoint import ParserCheckpointStore

try:
    from page_capability_pdf_inspector import (
        classify_pdf_pages as classify_pdf_pages_enhanced,
    )
    from pdf_inspector_bridge import health_check as pdf_inspector_health

    _PDF_INSPECTOR_AVAILABLE = True
except ImportError:  # pragma: no cover
    from page_capability import classify_pdf_pages as classify_pdf_pages_enhanced

    _PDF_INSPECTOR_AVAILABLE = False

    def pdf_inspector_health() -> dict:
        return {"enabled": False, "library_loaded": False}


def classify_pdf_pages(*args, **kwargs):
    """Compatibility wrapper: enhanced path returns (pages, inspector); mocks may return list."""
    result = classify_pdf_pages_enhanced(*args, **kwargs)
    if isinstance(result, tuple) and len(result) == 2:
        return result
    return result, None


def env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return int(raw)


BATCH_SIZE = env_int("PARSER_BATCH_SIZE", 5)
BATCH_TIMEOUT_SECONDS = env_int("PARSER_BATCH_TIMEOUT_SECONDS", 600)
PAGE_TIMEOUT_SECONDS = env_int("PARSER_PAGE_TIMEOUT_SECONDS", 240)
DOCUMENT_ORCHESTRATION_TIMEOUT_SECONDS = env_int(
    "PARSER_DOCUMENT_ORCHESTRATION_TIMEOUT_SECONDS", 43200
)
MAX_BATCH_ATTEMPTS = env_int("PARSER_MAX_BATCH_ATTEMPTS", 3)
NATIVE_MIN_CHARS = env_int("PARSER_NATIVE_TEXT_MIN_CHARS", 40)
IMAGES_SCALE = float(os.getenv("PARSER_IMAGES_SCALE", "0.85"))

ProgressCallback = Callable[[dict[str, Any]], None]
CancelCallback = Callable[[], bool]


class BoundedParseError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def apply_ocr_mode(
    pages: list[PageCapability], ocr_mode: str
) -> list[PageCapability]:
    adjusted: list[PageCapability] = []
    for page in pages:
        if ocr_mode == "FORCED":
            adjusted.append(
                PageCapability(
                    **{
                        **page.to_dict(),
                        "ocrRequired": True,
                        "whyOcrRequired": "ocr_mode_forced",
                        "capability": (
                            "SCANNED_IMAGE"
                            if page.nativeTextCharacters < NATIVE_MIN_CHARS
                            else page.capability
                        ),
                    }
                )
            )
        elif ocr_mode == "DISABLED":
            adjusted.append(
                PageCapability(
                    **{
                        **page.to_dict(),
                        "ocrRequired": False,
                        "whyOcrRequired": None,
                    }
                )
            )
        else:
            adjusted.append(page)
    return adjusted


def remap_page_numbers(result: dict[str, Any], page_start: int) -> dict[str, Any]:
    pages = result.get("pages") or []
    if not pages:
        return result
    reported = [int(page.get("pageNumber") or 0) for page in pages]
    minimum = min(reported)
    offset = 0
    if minimum == 1 and page_start > 1:
        offset = page_start - 1
    elif minimum != page_start and minimum >= 1:
        offset = page_start - minimum

    def shift_page(value: int) -> int:
        return value + offset

    for page in pages:
        page["pageNumber"] = shift_page(int(page.get("pageNumber") or 1))
    for clause in result.get("clauses") or []:
        clause["pageStart"] = shift_page(int(clause.get("pageStart") or 1))
        clause["pageEnd"] = shift_page(int(clause.get("pageEnd") or clause["pageStart"]))
        for box in clause.get("boundingBoxes") or []:
            box["page"] = shift_page(int(box.get("page") or 1))
    for table in result.get("tables") or []:
        table["pageStart"] = shift_page(int(table.get("pageStart") or 1))
        table["pageEnd"] = shift_page(int(table.get("pageEnd") or table["pageStart"]))
        for box in table.get("boundingBoxes") or []:
            box["page"] = shift_page(int(box.get("page") or 1))
    for warning in result.get("warnings") or []:
        if warning.get("pageNumber") is not None:
            warning["pageNumber"] = shift_page(int(warning["pageNumber"]))
    return result


def merge_batch_results(
    document_version_id: str,
    language: str,
    batch_results: list[dict[str, Any]],
    *,
    expected_page_count: int,
    plan: dict[str, Any],
) -> dict[str, Any]:
    pages_by_number: dict[int, dict[str, Any]] = {}
    clauses: list[dict[str, Any]] = []
    tables: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    seen_clause_hashes: set[str] = set()
    seen_table_hashes: set[str] = set()
    quality_values: list[float] = []
    providers: set[str] = set()

    for batch in batch_results:
        providers.add(str(batch.get("provider") or "DOCLING"))
        for page in batch.get("pages") or []:
            page_number = int(page["pageNumber"])
            if page_number in pages_by_number:
                raise BoundedParseError(
                    "DUPLICATE_PAGE_BLOCK",
                    f"Duplicate page payload for page {page_number}",
                )
            pages_by_number[page_number] = page
            quality_values.append(float(page.get("textQualityScore") or 0.0))
        for clause in batch.get("clauses") or []:
            content_hash = str(clause.get("contentHash") or "")
            if content_hash and content_hash in seen_clause_hashes:
                continue
            if content_hash:
                seen_clause_hashes.add(content_hash)
            clause = dict(clause)
            clause["sortOrder"] = len(clauses)
            clauses.append(clause)
        for table in batch.get("tables") or []:
            content_hash = str(table.get("contentHash") or "")
            if content_hash and content_hash in seen_table_hashes:
                continue
            if content_hash:
                seen_table_hashes.add(content_hash)
            tables.append(table)
        warnings.extend(batch.get("warnings") or [])

    missing = [page for page in range(1, expected_page_count + 1) if page not in pages_by_number]
    if missing:
        raise BoundedParseError(
            "MISSING_PAGES",
            f"Canonical merge missing pages: {missing[:20]}",
        )
    pages = [pages_by_number[number] for number in range(1, expected_page_count + 1)]
    quality = sum(quality_values) / len(quality_values) if quality_values else 0.0
    layout_block_count = sum(
        1 for page in pages if len((page.get("rawText") or "").strip()) > 0
    )
    if layout_block_count == 0:
        raise BoundedParseError("QUALITY_GATE_FAILED", "No layout text blocks were produced")

    return {
        "documentVersionId": document_version_id,
        "provider": "BOUNDED_DOCLING" if "DOCLING" in providers else next(iter(providers)),
        "providerVersion": "bounded-v1",
        "pageCount": expected_page_count,
        "language": language,
        "textQualityScore": quality,
        "pages": pages,
        "clauses": clauses,
        "tables": tables,
        "warnings": warnings,
        "metadata": {
            "terminalStatus": "READY",
            "processedPageCount": expected_page_count,
            "failedPageCount": 0,
            "failedPages": [],
            "layoutBlockCount": layout_block_count,
            "checkpointCount": plan.get("checkpointCount", 0),
            "parserPlan": plan,
            "qualityGate": "PASS",
        },
    }


def native_pages_result(
    path: Path,
    page_numbers: list[int],
    request: Any,
    *,
    capabilities: dict[int, PageCapability],
) -> dict[str, Any]:
    from pypdf import PdfReader

    reader = PdfReader(str(path))
    pages: list[dict[str, Any]] = []
    clauses: list[dict[str, Any]] = []
    for page_number in page_numbers:
        page = reader.pages[page_number - 1]
        try:
            text = page.extract_text() or ""
        except Exception:
            text = ""
        mediabox = page.mediabox
        width = float(mediabox.width) if mediabox is not None else 1.0
        height = float(mediabox.height) if mediabox is not None else 1.0
        quality = min(1.0, len(text.strip()) / 500.0)
        pages.append(
            {
                "pageNumber": page_number,
                "width": width,
                "height": height,
                "rotation": int(page.get("/Rotate") or 0),
                "rawText": text,
                "normalizedText": " ".join(text.split()),
                "textQualityScore": quality,
                "thumbnailObjectKey": None,
                "metadata": {
                    "provider": "NATIVE_TEXT_PROVIDER",
                    "capability": capabilities[page_number].capability,
                },
            }
        )
        body = text.strip()
        if len(body) >= 80:
            clauses.append(
                {
                    "sourceId": f"native-{page_number}",
                    "parentSourceId": None,
                    "clauseNumber": f"P{page_number}",
                    "title": f"Page {page_number}",
                    "rawText": body[:20000],
                    "normalizedText": " ".join(body.split()),
                    "clauseType": "PAGE",
                    "pageStart": page_number,
                    "pageEnd": page_number,
                    "boundingBoxes": [],
                    "contentHash": hashlib.sha256(body.encode("utf-8")).hexdigest(),
                    "sortOrder": page_number,
                    "metadata": {"provider": "NATIVE_TEXT_PROVIDER", "fallback": False},
                }
            )
    return {
        "documentVersionId": str(request.documentVersionId),
        "provider": "NATIVE_TEXT_PROVIDER",
        "providerVersion": "pypdf",
        "pageCount": len(pages),
        "language": request.languageHint or "und",
        "textQualityScore": (
            sum(page["textQualityScore"] for page in pages) / len(pages) if pages else 0.0
        ),
        "pages": pages,
        "clauses": clauses,
        "tables": [],
        "warnings": [],
        "metadata": {"ocrMode": request.ocrMode, "route": "NATIVE"},
    }


def convert_docling_batch(
    path: Path,
    request: Any,
    *,
    page_start: int,
    page_end: int,
    ocr_required: bool,
    extract_tables: bool,
) -> dict[str, Any]:
    from docling.datamodel.base_models import InputFormat
    from docling.datamodel.pipeline_options import EasyOcrOptions, PdfPipelineOptions
    from docling.document_converter import DocumentConverter, PdfFormatOption

    import app as app_module

    options = PdfPipelineOptions()
    options.do_ocr = ocr_required
    options.images_scale = IMAGES_SCALE
    hint = (request.languageHint or "tr").lower()
    lang = ["tr", "en"] if hint.startswith("tr") else ["en"]
    if ocr_required:
        options.ocr_options = EasyOcrOptions(lang=lang, force_full_page_ocr=True)
        options.do_table_structure = False
    else:
        options.do_table_structure = extract_tables
    converter = DocumentConverter(
        format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)}
    )
    converted = converter.convert(path, page_range=(page_start, page_end))
    exported = converted.document.export_to_dict()
    result = app_module.provider_neutral_result(exported, converted, request)
    result = remap_page_numbers(result, page_start)
    result["metadata"] = {
        **(result.get("metadata") or {}),
        "route": "OCR" if ocr_required else "DOCLING",
        "pageStart": page_start,
        "pageEnd": page_end,
        "ocrUsed": ocr_required,
    }
    return result


def _unit_work_items(batch: dict[str, Any]) -> list[dict[str, Any]]:
    """Split a capability-homogeneous batch into executable units (for retry shrink)."""
    return [batch]


def _shrink_units(units: list[dict[str, Any]]) -> list[dict[str, Any]]:
    shrunk: list[dict[str, Any]] = []
    for unit in units:
        numbers = unit["pageNumbers"]
        if len(numbers) <= 1:
            shrunk.append(unit)
            continue
        for page_number in numbers:
            shrunk.append(
                {
                    **unit,
                    "pageStart": page_number,
                    "pageEnd": page_number,
                    "pageNumbers": [page_number],
                }
            )
    return shrunk


def _execute_unit(
    *,
    path: Path,
    request: Any,
    unit: dict[str, Any],
    capability_map: dict[int, PageCapability],
    executor: ThreadPoolExecutor,
) -> tuple[dict[str, Any], str]:
    if unit["route"] == "NATIVE":
        future = executor.submit(
            native_pages_result,
            path,
            unit["pageNumbers"],
            request,
            capabilities=capability_map,
        )
        stage = "NATIVE_TEXT_EXTRACTION"
    else:
        ocr_required = unit["route"] == "OCR"
        extract_tables = bool(request.extractTables) and unit["route"] != "OCR"
        stage = "OCR" if ocr_required else (
            "TABLE_DETECTION" if unit["route"] == "TABLE" else "DOCLING_CONVERSION"
        )
        future = executor.submit(
            convert_docling_batch,
            path,
            request,
            page_start=unit["pageStart"],
            page_end=unit["pageEnd"],
            ocr_required=ocr_required,
            extract_tables=extract_tables,
        )
    page_span = unit["pageEnd"] - unit["pageStart"] + 1
    timeout = min(
        BATCH_TIMEOUT_SECONDS,
        max(PAGE_TIMEOUT_SECONDS, PAGE_TIMEOUT_SECONDS * page_span),
    )
    return future.result(timeout=timeout), stage


def run_bounded_parse(
    *,
    path: Path,
    request: Any,
    job_id: str,
    checkpoint_store: ParserCheckpointStore,
    executor: ThreadPoolExecutor,
    on_progress: ProgressCallback,
    is_cancelled: CancelCallback,
) -> dict[str, Any]:
    started = time.monotonic()
    if request.mimeType != "application/pdf":
        raise BoundedParseError("UNSUPPORTED_FOR_BOUNDED", "Bounded parse is PDF-only")

    on_progress(
        {
            "stage": "PAGE_ENUMERATION",
            "progress": 20,
            "message": "Classifying page capabilities",
            "currentStage": "PAGE_ENUMERATION",
        }
    )
    classified = classify_pdf_pages(path, native_min_chars=NATIVE_MIN_CHARS)
    if isinstance(classified, tuple) and len(classified) == 2:
        capabilities, inspector_result = classified
    else:
        # Unit tests / legacy mocks may still return a bare page list.
        capabilities, inspector_result = classified, None
    if inspector_result is not None:
        on_progress(
            {
                "stage": "pdf_inspector",
                "progress": 22,
                "message": "pdf-inspector classification complete",
                "currentStage": "PDF_INSPECTOR",
                "pdf_type": inspector_result.pdf_type,
                "confidence": inspector_result.confidence,
                "duration_ms": inspector_result.duration_ms,
                "pages_needing_ocr": len(inspector_result.pages_needing_ocr),
                "usable": inspector_result.is_usable,
                "error": inspector_result.error,
            }
        )
    # Optional fast-path hook: pure text_based + markdown is available for
    # a future clause extractor short-circuit (Docling path remains default).
    if (
        inspector_result
        and inspector_result.is_usable
        and inspector_result.pdf_type == "text_based"
        and inspector_result.markdown
    ):
        on_progress(
            {
                "stage": "pdf_inspector",
                "progress": 23,
                "message": "pdf-inspector markdown available for text_based document",
                "currentStage": "PDF_INSPECTOR_MARKDOWN",
                "markdownChars": len(inspector_result.markdown),
            }
        )
    capabilities = apply_ocr_mode(capabilities, request.ocrMode)
    total_pages = len(capabilities)
    if total_pages == 0:
        raise BoundedParseError("EMPTY_DOCUMENT", "PDF has no pages")
    if total_pages > env_int("MAX_PAGE_COUNT", 500):
        raise BoundedParseError("PAGE_COUNT_LIMIT", "Document exceeds the page limit")

    batches = build_batches(capabilities, batch_size=BATCH_SIZE)
    capability_map = {page.pageNumber: page for page in capabilities}
    prior = checkpoint_store.latest_success_by_batch(job_id)

    batch_results: list[dict[str, Any]] = []
    provider_per_page: dict[int, str] = {}
    ocr_pages: list[int] = []
    docling_pages: list[int] = []
    native_pages: list[int] = []
    fallback_pages: list[int] = []
    checkpoint_count = 0
    slowest_batch: dict[str, Any] | None = None

    on_progress(
        {
            "stage": "PARSING",
            "progress": 30,
            "message": f"Parsing {total_pages} pages in {len(batches)} batches",
            "currentStage": "BOUNDED_BATCH_PARSE",
            "totalPages": total_pages,
            "classifiedPages": total_pages,
            "processedPages": 0,
            "failedPages": 0,
            "estimatedRemainingBatches": len(batches),
            # Durable routing signal (PDF_INSPECTOR stage is ~sub-second and easy to miss).
            "pdf_type": getattr(inspector_result, "pdf_type", None) if inspector_result else None,
            "confidence": getattr(inspector_result, "confidence", None) if inspector_result else None,
            "duration_ms": getattr(inspector_result, "duration_ms", None) if inspector_result else None,
            "pages_needing_ocr": (
                len(inspector_result.pages_needing_ocr) if inspector_result else None
            ),
            "usable": getattr(inspector_result, "is_usable", None) if inspector_result else None,
        }
    )

    for batch in batches:
        if is_cancelled():
            raise BoundedParseError("CANCELLED", "Parser cancellation requested")
        if time.monotonic() - started > DOCUMENT_ORCHESTRATION_TIMEOUT_SECONDS:
            raise BoundedParseError(
                "DOCUMENT_ORCHESTRATION_TIMEOUT",
                "Document orchestration timeout exceeded",
            )

        batch_number = int(batch["batchNumber"])
        if batch_number in prior and prior[batch_number].get("result"):
            reused = prior[batch_number]["result"]
            batch_results.append(reused)
            checkpoint_count += 1
            for page_number in batch["pageNumbers"]:
                provider = str(reused.get("provider") or "CHECKPOINT")
                provider_per_page[page_number] = provider
                if batch["ocrRequired"]:
                    ocr_pages.append(page_number)
                elif provider == "NATIVE_TEXT_PROVIDER":
                    native_pages.append(page_number)
                else:
                    docling_pages.append(page_number)
            continue

        units = _unit_work_items(batch)
        attempt = 1
        pending = list(units)
        while pending and attempt <= MAX_BATCH_ATTEMPTS:
            if is_cancelled():
                raise BoundedParseError("CANCELLED", "Parser cancellation requested")
            next_pending: list[dict[str, Any]] = []
            for unit in pending:
                unit_started = utc_now()
                unit_t0 = time.monotonic()
                checkpoint_key = (
                    batch_number
                    if unit["pageNumbers"] == batch["pageNumbers"]
                    else batch_number * 1000 + unit["pageStart"]
                )
                try:
                    result, stage = _execute_unit(
                        path=path,
                        request=request,
                        unit=unit,
                        capability_map=capability_map,
                        executor=executor,
                    )
                    duration_ms = int((time.monotonic() - unit_t0) * 1000)
                    if slowest_batch is None or duration_ms > slowest_batch["durationMs"]:
                        slowest_batch = {
                            "batchNumber": batch_number,
                            "pageStart": unit["pageStart"],
                            "pageEnd": unit["pageEnd"],
                            "stage": stage,
                            "durationMs": duration_ms,
                        }
                    text_blocks = sum(
                        1
                        for page in result.get("pages") or []
                        if len((page.get("rawText") or "").strip()) > 0
                    )
                    checkpoint_store.save(
                        {
                            "id": str(uuid.uuid4()),
                            "organization_id": None,
                            "document_version_id": str(request.documentVersionId),
                            "parser_job_id": job_id,
                            "page_number": unit["pageStart"],
                            "batch_number": checkpoint_key,
                            "provider": result.get("provider") or "DOCLING",
                            "provider_version": result.get("providerVersion"),
                            "status": "SUCCEEDED",
                            "attempt": attempt,
                            "started_at": unit_started,
                            "completed_at": utc_now(),
                            "duration_ms": duration_ms,
                            "text_block_count": text_blocks,
                            "layout_block_count": text_blocks,
                            "table_count": len(result.get("tables") or []),
                            "ocr_used": unit["route"] == "OCR",
                            "quality_status": "PASS" if text_blocks > 0 else "LOW",
                            "error_code": None,
                            "artifact_reference": None,
                            "result": result,
                        }
                    )
                    checkpoint_count += 1
                    batch_results.append(result)
                    for page_number in unit["pageNumbers"]:
                        provider = str(result.get("provider") or "DOCLING")
                        provider_per_page[page_number] = provider
                        if unit["route"] == "OCR":
                            ocr_pages.append(page_number)
                        elif provider == "NATIVE_TEXT_PROVIDER":
                            native_pages.append(page_number)
                        else:
                            docling_pages.append(page_number)
                        if attempt > 1 or unit["pageNumbers"] != batch["pageNumbers"]:
                            fallback_pages.append(page_number)
                    on_progress(
                        {
                            "stage": "PARSING",
                            "progress": min(
                                95, 30 + int(60 * len(provider_per_page) / max(1, total_pages))
                            ),
                            "message": (
                                f"Batch {batch_number}/{len(batches)} "
                                f"pages {unit['pageStart']}-{unit['pageEnd']} ({stage})"
                            ),
                            "currentStage": stage,
                            "currentBatch": batch_number,
                            "currentPage": unit["pageStart"],
                            "totalPages": total_pages,
                            "classifiedPages": total_pages,
                            "processedPages": len(provider_per_page),
                            "failedPages": 0,
                            "estimatedRemainingBatches": max(0, len(batches) - batch_number),
                        }
                    )
                except FuturesTimeout:
                    checkpoint_store.save(
                        {
                            "id": str(uuid.uuid4()),
                            "organization_id": None,
                            "document_version_id": str(request.documentVersionId),
                            "parser_job_id": job_id,
                            "page_number": unit["pageStart"],
                            "batch_number": checkpoint_key,
                            "provider": "DOCLING",
                            "provider_version": None,
                            "status": "FAILED",
                            "attempt": attempt,
                            "started_at": unit_started,
                            "completed_at": utc_now(),
                            "duration_ms": int((time.monotonic() - unit_t0) * 1000),
                            "text_block_count": 0,
                            "layout_block_count": 0,
                            "table_count": 0,
                            "ocr_used": unit["route"] == "OCR",
                            "quality_status": "FAIL",
                            "error_code": "BATCH_TIMEOUT",
                            "artifact_reference": None,
                            "result": None,
                        }
                    )
                    next_pending.append(unit)
                except Exception:
                    checkpoint_store.save(
                        {
                            "id": str(uuid.uuid4()),
                            "organization_id": None,
                            "document_version_id": str(request.documentVersionId),
                            "parser_job_id": job_id,
                            "page_number": unit["pageStart"],
                            "batch_number": checkpoint_key,
                            "provider": "DOCLING",
                            "provider_version": None,
                            "status": "FAILED",
                            "attempt": attempt,
                            "started_at": unit_started,
                            "completed_at": utc_now(),
                            "duration_ms": int((time.monotonic() - unit_t0) * 1000),
                            "text_block_count": 0,
                            "layout_block_count": 0,
                            "table_count": 0,
                            "ocr_used": unit["route"] == "OCR",
                            "quality_status": "FAIL",
                            "error_code": "BATCH_FAILED",
                            "artifact_reference": None,
                            "result": None,
                        }
                    )
                    next_pending.append(unit)
            if not next_pending:
                break
            pending = _shrink_units(next_pending)
            attempt += 1

        unfinished = [
            number for number in batch["pageNumbers"] if number not in provider_per_page
        ]
        if unfinished:
            raise BoundedParseError(
                "PARTIAL_PARSE_FAILED",
                f"Failed pages remain after retries: {unfinished[:30]}",
            )

    plan = {
        "totalPages": total_pages,
        "batchCount": len(batches),
        "batchSize": BATCH_SIZE,
        "nativeTextPages": sorted(set(native_pages)),
        "ocrPages": sorted(set(ocr_pages)),
        "doclingPages": sorted(set(docling_pages)),
        "fallbackPages": sorted(set(fallback_pages)),
        "providerPerPage": {
            str(key): value for key, value in sorted(provider_per_page.items())
        },
        "checkpointCount": checkpoint_count,
        "failedPages": [],
        "slowestBatch": slowest_batch,
        "capabilities": [page.to_dict() for page in capabilities],
        "batches": [
            {
                "batchNumber": item["batchNumber"],
                "route": item["route"],
                "pageStart": item["pageStart"],
                "pageEnd": item["pageEnd"],
                "pageNumbers": item["pageNumbers"],
            }
            for item in batches
        ],
        "durationMs": int((time.monotonic() - started) * 1000),
    }
    on_progress(
        {
            "stage": "FINALIZATION",
            "progress": 97,
            "message": "Merging canonical page results",
            "currentStage": "FINALIZATION",
            "totalPages": total_pages,
            "processedPages": total_pages,
            "failedPages": 0,
            "classifiedPages": total_pages,
            "estimatedRemainingBatches": 0,
        }
    )
    return merge_batch_results(
        str(request.documentVersionId),
        request.languageHint or "und",
        batch_results,
        expected_page_count=total_pages,
        plan=plan,
    )
