"""Enhanced page capability classification that prefers pdf-inspector when available.

This module is a drop-in enhancement of the existing page_capability.py.
It keeps the original public API (classify_pdf_pages, build_batches, PageCapability)
while adding a fast, high-quality path.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from page_capability import (  # original module – kept as source of truth for data model
    NATIVE_MIN_CHARS,
    PageCapability,
    build_batches,
    classify_page_signals,
    classify_pdf_pages as legacy_classify_pdf_pages,
)
from pdf_inspector_bridge import (
    PdfInspectorResult,
    enrich_page_capabilities_from_inspector,
    inspect_pdf,
)

logger = logging.getLogger("specai.document.page_capability")


def classify_pdf_pages(
    path: str | Path,
    *,
    native_min_chars: int = NATIVE_MIN_CHARS,
    prefer_pdf_inspector: bool = True,
) -> tuple[list[PageCapability], PdfInspectorResult | None]:
    """Classify every page.

    Returns
    -------
    (pages, inspector_result)
        inspector_result is None when the fast path was not used or failed.
    """
    path = Path(path)
    inspector: PdfInspectorResult | None = None

    if prefer_pdf_inspector:
        # 1. Fast whole-document classification + Markdown (if cheap)
        inspector = inspect_pdf(path, extract_markdown=True)

        if inspector.is_usable and inspector.pdf_type == "text_based":
            # Pure digital PDF – we can skip the heavier pypdf + render path
            # for classification and only build lightweight PageCapability objects.
            pages = _build_native_pages_from_inspector(inspector, native_min_chars)
            logger.info(
                "pdf-inspector fast path used",
                extra={
                    "pdf_type": inspector.pdf_type,
                    "confidence": inspector.confidence,
                    "pages": inspector.page_count,
                    "duration_ms": inspector.duration_ms,
                },
            )
            return pages, inspector

        if inspector.is_usable and inspector.pdf_type in {"mixed", "scanned", "image_based"}:
            # Still run legacy classifier for fine-grained signals, then overlay
            # the authoritative OCR page list from pdf-inspector.
            pages = legacy_classify_pdf_pages(path, native_min_chars=native_min_chars)
            pages = enrich_page_capabilities_from_inspector(pages, inspector)
            logger.info(
                "pdf-inspector hybrid path used",
                extra={
                    "pdf_type": inspector.pdf_type,
                    "confidence": inspector.confidence,
                    "ocr_pages": len(inspector.pages_needing_ocr),
                    "duration_ms": inspector.duration_ms,
                },
            )
            return pages, inspector

    # Fallback – original behaviour
    pages = legacy_classify_pdf_pages(path, native_min_chars=native_min_chars)
    return pages, inspector


def _synthetic_native_text(native_min_chars: int) -> str:
    """Multi-line non-whitespace text that satisfies NATIVE_TEXT thresholds.

    Whitespace-only strings strip to empty and incorrectly become LOW_CONTENT.
    """
    line = "native digital text sample "
    # Ensure enough characters after strip + at least NATIVE_MIN_BLOCKS lines.
    body = "\n".join([line * 2, line * 2, line * 2])
    if len(body.strip()) < native_min_chars + 10:
        body = (line * ((native_min_chars // len(line)) + 3)) + "\n" + body
    return body


def _build_native_pages_from_inspector(
    inspector: PdfInspectorResult,
    native_min_chars: int,
) -> list[PageCapability]:
    """Construct PageCapability list for a confirmed text-based PDF without re-parsing."""
    pages: list[PageCapability] = []
    synthetic = _synthetic_native_text(native_min_chars)
    has_md = bool(inspector.markdown and str(inspector.markdown).strip())
    prefer_docling_native = (not has_md) and float(inspector.confidence or 0.0) >= 0.90
    for i in range(1, inspector.page_count + 1):
        # We do not have per-page character counts from the high-level API,
        # so we assume healthy digital text (the classifier already confirmed it).
        page = classify_page_signals(
            page_number=i,
            text=synthetic,
            image_count=0,
            font_count=2,
            rotation=0,
            text_extraction_ms=0,
            render_ms=0,
            image_coverage=0.0,
            vector_complexity=0.0,
            native_min_chars=native_min_chars,
        )
        if prefer_docling_native:
            # Empty markdown + high-conf text_based: pypdf page dumps are weak;
            # prefer Docling layout extract with OCR disabled.
            data = page.to_dict()
            data["capability"] = "VECTOR_COMPLEX"
            signals = dict(data.get("qualitySignals") or {})
            signals["preferDoclingNative"] = True
            data["qualitySignals"] = signals
            page = PageCapability(**data)
        pages.append(page)
    # Overlay any residual OCR signals (softened for high-conf text_based).
    return enrich_page_capabilities_from_inspector(pages, inspector)


# Re-export so callers can still do "from page_capability_pdf_inspector import build_batches"
__all__ = [
    "PageCapability",
    "build_batches",
    "classify_pdf_pages",
    "classify_page_signals",
]
