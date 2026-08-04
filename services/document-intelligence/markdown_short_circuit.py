"""Markdown short-circuit for pure text-based PDFs.

When pdf-inspector returns a high-confidence text_based result with usable
Markdown, skip Docling native batches and feed Markdown into the existing
provider-neutral result schema (pages / clauses / tables / metadata).
"""

from __future__ import annotations

import hashlib
import logging
import os
import re
import time
from typing import Any, Callable

logger = logging.getLogger("specai.document.markdown_short_circuit")

ProgressCallback = Callable[[dict[str, Any]], None]

CLAUSE_NUMBER_RE = re.compile(
    r"^(\d+(?:\.\d+)*|[A-ZÇĞİÖŞÜ]\d*(?:\.\d+)*)\b",
    re.IGNORECASE,
)


def _env_bool(name: str, default: str = "true") -> bool:
    return os.getenv(name, default).lower() in {"1", "true", "yes"}


def _env_float(name: str, default: str) -> float:
    return float(os.getenv(name, default))


def _env_int(name: str, default: str) -> int:
    return int(os.getenv(name, default))


def should_short_circuit(
    inspector_result: Any,
    *,
    ocr_mode: str = "AUTO",
    allow_short_circuit: bool = True,
    force_mode: str = "AUTO",
) -> bool:
    """Pure decision function – easy to unit-test."""
    mode = str(force_mode or "AUTO").upper()
    if mode in {"FORCE_DOCLING", "FORCE_OCR"}:
        return False
    if not allow_short_circuit:
        return False
    if not _env_bool("PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "true") and mode != "FORCE_SHORT_CIRCUIT":
        return False
    if str(ocr_mode or "AUTO").upper() == "FORCED" and mode != "FORCE_SHORT_CIRCUIT":
        return False
    if inspector_result is None:
        return False
    if mode != "FORCE_SHORT_CIRCUIT" and not getattr(inspector_result, "is_usable", False):
        return False
    if mode != "FORCE_SHORT_CIRCUIT" and getattr(inspector_result, "pdf_type", None) != "text_based":
        return False
    if mode != "FORCE_SHORT_CIRCUIT":
        min_conf = _env_float("PDF_INSPECTOR_MARKDOWN_MIN_CONFIDENCE", "0.90")
        if float(getattr(inspector_result, "confidence", 0.0)) < min_conf:
            return False
    markdown = getattr(inspector_result, "markdown", None)
    if not markdown or not isinstance(markdown, str):
        return False
    max_chars = _env_int("PDF_INSPECTOR_MARKDOWN_MAX_CHARS", "500000")
    if len(markdown) > max_chars:
        logger.info(
            "markdown short-circuit skipped – too large",
            extra={"chars": len(markdown), "max_chars": max_chars},
        )
        return False
    if int(getattr(inspector_result, "page_count", 0) or 0) <= 0:
        return False
    return True


def _normalize_text(value: str) -> str:
    return " ".join((value or "").split())


def _content_hash(value: str) -> str:
    return hashlib.sha256((value or "").encode("utf-8")).hexdigest()


def _extract_clause_number(title: str | None, fallback: int) -> str:
    if title:
        match = CLAUSE_NUMBER_RE.match(title.strip())
        if match:
            return match.group(1)
    return f"C{fallback}"


def _split_markdown_across_pages(markdown: str, page_count: int) -> list[str]:
    """Approximate per-page rawText so quality gate / page persistence stay valid."""
    lines = markdown.splitlines()
    if page_count <= 1:
        return [markdown]
    if not lines:
        return [""] * page_count
    chunk = max(1, (len(lines) + page_count - 1) // page_count)
    pages: list[str] = []
    for index in range(page_count):
        start = index * chunk
        end = min(len(lines), start + chunk)
        if start >= len(lines):
            pages.append(f"[page {index + 1} covered by document markdown]")
        else:
            pages.append("\n".join(lines[start:end]))
    return pages


def _build_clauses(markdown: str, page_count: int) -> list[dict[str, Any]]:
    clauses: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None
    clause_index = 0

    def close_current() -> None:
        nonlocal current
        if not current:
            return
        body = (current.get("rawText") or "").strip()
        if not body:
            current = None
            return
        current["rawText"] = body
        current["normalizedText"] = _normalize_text(body)
        current["contentHash"] = _content_hash(body)
        current["sortOrder"] = len(clauses)
        clauses.append(current)
        current = None

    for line in markdown.splitlines():
        stripped = line.strip()
        if not stripped:
            if current is not None:
                current["rawText"] += "\n"
            continue

        if stripped.startswith("#"):
            close_current()
            level = len(stripped) - len(stripped.lstrip("#"))
            title = stripped.lstrip("#").strip() or f"Section {clause_index + 1}"
            clause_index += 1
            current = {
                "sourceId": f"pdf-inspector-md-{clause_index}",
                "parentSourceId": None,
                "clauseNumber": _extract_clause_number(title, clause_index),
                "title": title[:500],
                "rawText": title + "\n",
                "clauseType": f"HEADING_H{min(level, 6)}",
                "pageStart": 1,
                "pageEnd": page_count,
                "boundingBoxes": [],
                "metadata": {
                    "provider": "PDF_INSPECTOR",
                    "source": "PDF_INSPECTOR_MARKDOWN",
                    "headingLevel": level,
                    "shortCircuited": True,
                },
            }
            continue

        if current is None:
            clause_index += 1
            current = {
                "sourceId": f"pdf-inspector-md-{clause_index}",
                "parentSourceId": None,
                "clauseNumber": f"C{clause_index}",
                "title": f"Section {clause_index}",
                "rawText": "",
                "clauseType": "BODY",
                "pageStart": 1,
                "pageEnd": page_count,
                "boundingBoxes": [],
                "metadata": {
                    "provider": "PDF_INSPECTOR",
                    "source": "PDF_INSPECTOR_MARKDOWN",
                    "shortCircuited": True,
                    "fallback": True,
                },
            }
        current["rawText"] += stripped + "\n"

    close_current()
    return clauses


def _build_tables(markdown: str, page_count: int) -> list[dict[str, Any]]:
    tables: list[dict[str, Any]] = []
    table_lines: list[str] = []
    in_table = False

    def flush() -> None:
        nonlocal table_lines, in_table
        if not table_lines:
            in_table = False
            return
        raw = "\n".join(table_lines)
        tables.append(
            {
                "pageStart": 1,
                "pageEnd": page_count,
                "caption": None,
                "markdownContent": raw,
                "structuredContent": {"rowCount": len(table_lines)},
                "boundingBoxes": [],
                "contentHash": _content_hash(raw),
            }
        )
        table_lines = []
        in_table = False

    for line in markdown.splitlines():
        stripped = line.strip()
        if "|" in stripped and stripped.startswith("|"):
            in_table = True
            table_lines.append(stripped)
        elif in_table:
            flush()
    if in_table:
        flush()
    return tables


def run_markdown_short_circuit(
    *,
    document_version_id: str,
    inspector_result: Any,
    language: str = "tr",
    ocr_mode: str = "AUTO",
    extract_tables: bool = True,
    progress_callback: ProgressCallback | None = None,
    started_monotonic: float | None = None,
) -> dict[str, Any]:
    """Build a result compatible with merge_batch_results / Java persistence."""
    wall_started = started_monotonic if started_monotonic is not None else time.monotonic()
    cpu_started = time.perf_counter()
    markdown: str = inspector_result.markdown
    page_count = int(getattr(inspector_result, "page_count", 1) or 1)

    if progress_callback:
        progress_callback(
            {
                "stage": "markdown_short_circuit",
                "progress": 35,
                "message": "Using pdf-inspector Markdown – Docling skipped",
                "currentStage": "MARKDOWN_SHORT_CIRCUIT",
                "pdf_type": inspector_result.pdf_type,
                "confidence": inspector_result.confidence,
                "duration_ms": getattr(inspector_result, "duration_ms", None),
                "pages_needing_ocr": len(getattr(inspector_result, "pages_needing_ocr", []) or []),
                "usable": True,
                "markdownChars": len(markdown),
            }
        )

    page_texts = _split_markdown_across_pages(markdown, page_count)
    pages: list[dict[str, Any]] = []
    for index, text in enumerate(page_texts, start=1):
        pages.append(
            {
                "pageNumber": index,
                "width": 1.0,
                "height": 1.0,
                "rotation": 0,
                "rawText": text,
                "normalizedText": _normalize_text(text),
                "textQualityScore": min(1.0, max(0.5, len(text.strip()) / 500.0)),
                "thumbnailObjectKey": None,
                "metadata": {
                    "provider": "PDF_INSPECTOR",
                    "capability": "NATIVE_TEXT",
                    "shortCircuited": True,
                },
            }
        )

    try:
        from markdown_clause_parser import parse_markdown_clauses

        clauses, parsed_tables = parse_markdown_clauses(
            markdown, page_count=page_count, provider="PDF_INSPECTOR"
        )
        for clause in clauses:
            meta = dict(clause.get("metadata") or {})
            meta["shortCircuited"] = True
            clause["metadata"] = meta
        tables = parsed_tables if extract_tables else []
    except Exception:
        logger.exception("hierarchical clause parser failed – using legacy splitter")
        clauses = _build_clauses(markdown, page_count)
        tables = _build_tables(markdown, page_count) if extract_tables else []

    if not clauses:
        # Keep persistence usable even when Markdown has no headings.
        body = markdown.strip()
        if body:
            clauses = [
                {
                    "sourceId": "pdf-inspector-md-1",
                    "parentSourceId": None,
                    "clauseNumber": "C1",
                    "title": "Document",
                    "rawText": body[:200000],
                    "normalizedText": _normalize_text(body[:200000]),
                    "clauseType": "DOCUMENT",
                    "pageStart": 1,
                    "pageEnd": page_count,
                    "boundingBoxes": [],
                    "contentHash": _content_hash(body[:200000]),
                    "sortOrder": 0,
                    "metadata": {
                        "provider": "PDF_INSPECTOR",
                        "source": "PDF_INSPECTOR_MARKDOWN",
                        "shortCircuited": True,
                        "fallback": True,
                    },
                }
            ]
    warnings: list[dict[str, Any]] = [
        {
            "warningCode": "MARKDOWN_SHORT_CIRCUIT",
            "severity": "INFO",
            "message": "Docling skipped; pdf-inspector Markdown used for text_based document",
            "pageNumber": None,
            "metadata": {
                "markdownChars": len(markdown),
                "confidence": float(inspector_result.confidence),
            },
        }
    ]

    short_ms = int((time.perf_counter() - cpu_started) * 1000)
    total_ms = int((time.monotonic() - wall_started) * 1000)
    quality = (
        sum(float(page["textQualityScore"]) for page in pages) / len(pages) if pages else 0.0
    )
    plan = {
        "totalPages": page_count,
        "batchCount": 0,
        "batchSize": 0,
        "nativeTextPages": list(range(1, page_count + 1)),
        "ocrPages": [],
        "doclingPages": [],
        "fallbackPages": [],
        "providerPerPage": {
            str(page): "PDF_INSPECTOR" for page in range(1, page_count + 1)
        },
        "checkpointCount": 0,
        "failedPages": [],
        "slowestBatch": None,
        "shortCircuited": True,
        "inspector": {
            "pdf_type": inspector_result.pdf_type,
            "confidence": float(inspector_result.confidence),
            "duration_ms": getattr(inspector_result, "duration_ms", 0),
            "markdown_chars": len(markdown),
        },
        "durationMs": total_ms,
        "shortCircuitMs": short_ms,
    }

    if progress_callback:
        progress_callback(
            {
                "stage": "markdown_short_circuit_done",
                "progress": 97,
                "message": (
                    f"Short-circuit completed – {len(clauses)} clauses, {len(tables)} tables"
                ),
                "currentStage": "MARKDOWN_SHORT_CIRCUIT_DONE",
                "pdf_type": inspector_result.pdf_type,
                "confidence": inspector_result.confidence,
                "duration_ms": short_ms,
                "pages_needing_ocr": 0,
                "usable": True,
                "markdownChars": len(markdown),
                "totalPages": page_count,
                "processedPages": page_count,
                "failedPages": 0,
            }
        )

    logger.info(
        "markdown short-circuit used",
        extra={
            "document_version_id": document_version_id,
            "clauses": len(clauses),
            "tables": len(tables),
            "duration_ms": short_ms,
            "markdown_chars": len(markdown),
            "ocr_mode": ocr_mode,
        },
    )

    result = {
        "documentVersionId": document_version_id,
        "provider": "PDF_INSPECTOR",
        "providerVersion": "pdf-inspector-short-circuit",
        "pageCount": page_count,
        "language": language or "und",
        "textQualityScore": quality,
        "pages": pages,
        "clauses": clauses,
        "tables": tables,
        "warnings": warnings,
        "metadata": {
            "terminalStatus": "READY",
            "processedPageCount": page_count,
            "failedPageCount": 0,
            "failedPages": [],
            "layoutBlockCount": sum(
                1 for page in pages if len((page.get("rawText") or "").strip()) > 0
            ),
            "checkpointCount": 0,
            "parserPlan": plan,
            "qualityGate": "PASS",
            "shortCircuited": True,
            "ocrMode": ocr_mode,
            "extractTables": extract_tables,
        },
    }
    try:
        from requirement_from_clauses import attach_requirements_to_result

        return attach_requirements_to_result(result)
    except Exception:
        logger.exception("requirement attachment failed – returning clauses only")
        return result
