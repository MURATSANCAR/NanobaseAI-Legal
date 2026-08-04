"""Production-grade bridge to firecrawl/pdf-inspector.

Responsibilities
----------------
* Fast whole-document classification (TextBased / Scanned / Mixed / ImageBased)
* High-quality native-text extraction with reading-order + table awareness
* Per-page OCR routing signals that feed the existing PageCapability model
* Strict timeouts, memory safety, and graceful fallback to the legacy pypdf path

Design constraints (aligned with DocumentIntelligence architecture)
------------------------------------------------------------------
* Never log document content
* Never raise unhandled exceptions into the job orchestrator
* Provider-neutral: returns the same shapes that page_capability / bounded_parser expect
* Optional: if the native package is missing or crashes, degrade silently
"""

from __future__ import annotations

import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

logger = logging.getLogger("specai.document.pdf_inspector")

# ---------------------------------------------------------------------------
# Configuration (env-driven, same style as the rest of the service)
# ---------------------------------------------------------------------------

PDF_INSPECTOR_TIMEOUT_SECONDS = float(os.getenv("PDF_INSPECTOR_TIMEOUT_SECONDS", "8.0"))
PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT = int(
    os.getenv("PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT", "120")
)
# Below this confidence we treat the classification as unreliable and fall back.
PDF_INSPECTOR_MIN_CONFIDENCE = float(os.getenv("PDF_INSPECTOR_MIN_CONFIDENCE", "0.55"))

PdfType = Literal["text_based", "scanned", "image_based", "mixed", "unknown"]


def _enabled() -> bool:
    return os.getenv("PDF_INSPECTOR_ENABLED", "true").lower() in {"1", "true", "yes"}


# Back-compat for tests / importers that read the module-level flag.
PDF_INSPECTOR_ENABLED = _enabled()


@dataclass(frozen=True)
class PdfInspectorResult:
    """Normalized result that the rest of the pipeline can consume."""

    available: bool
    pdf_type: PdfType
    confidence: float
    page_count: int
    pages_needing_ocr: list[int]  # 0-based
    markdown: str | None
    duration_ms: int
    error: str | None = None

    @property
    def is_usable(self) -> bool:
        return (
            self.available
            and self.error is None
            and self.confidence >= PDF_INSPECTOR_MIN_CONFIDENCE
            and self.pdf_type != "unknown"
        )

    @property
    def digital_text_ratio_estimate(self) -> float:
        """Rough signal compatible with existing DefaultDocumentParserRouter."""
        if not self.is_usable or self.page_count == 0:
            return 0.0
        if self.pdf_type == "text_based":
            return 0.95
        if self.pdf_type == "mixed":
            ocr_pages = len(self.pages_needing_ocr)
            return max(0.0, 1.0 - (ocr_pages / self.page_count))
        return 0.05  # scanned / image_based


# ---------------------------------------------------------------------------
# Lazy import – service must still start if the package is not installed
# ---------------------------------------------------------------------------

_pdf_inspector = None
_import_error: str | None = None


def _load_library() -> Any | None:
    global _pdf_inspector, _import_error
    if _pdf_inspector is not None:
        return _pdf_inspector
    if _import_error is not None:
        return None
    try:
        import pdf_inspector as pi  # type: ignore

        _pdf_inspector = pi
        return pi
    except Exception as exc:  # noqa: BLE001 – we intentionally swallow
        _import_error = f"{type(exc).__name__}: {exc}"
        logger.warning(
            "pdf-inspector not available – falling back to legacy classifier",
            extra={"error": _import_error},
        )
        return None


def _unavailable(error: str, *, duration_ms: int = 0) -> PdfInspectorResult:
    return PdfInspectorResult(
        available=False,
        pdf_type="unknown",
        confidence=0.0,
        page_count=0,
        pages_needing_ocr=[],
        markdown=None,
        duration_ms=duration_ms,
        error=error,
    )


def _normalize_ocr_page_indexes(pages: list[int], page_count: int) -> list[int]:
    """Normalize library page indexes to 0-based.

    Live pdf-inspector 0.2.x returns 1-based indexes (e.g. [1,2,3] for a 3-page
    scanned PDF). Older bindings may already be 0-based.
    """
    if not pages or page_count <= 0:
        return pages
    if 0 in pages:
        return [p for p in pages if 0 <= p < page_count]
    if min(pages) >= 1 and max(pages) <= page_count:
        return [p - 1 for p in pages]
    return [p for p in pages if 0 <= p < page_count]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def inspect_pdf(
    path: str | Path,
    *,
    timeout_seconds: float | None = None,
    extract_markdown: bool = True,
) -> PdfInspectorResult:
    """Run classification (+ optional Markdown extraction) under a hard timeout.

    This function is safe to call from the FastAPI worker threads.
    """
    if not _enabled():
        return _unavailable("disabled_by_config")

    lib = _load_library()
    if lib is None:
        return _unavailable("import_failed")

    path = Path(path)
    if not path.is_file():
        return _unavailable("file_not_found")

    timeout = timeout_seconds if timeout_seconds is not None else PDF_INSPECTOR_TIMEOUT_SECONDS
    started = time.perf_counter()

    def _work() -> dict[str, Any]:
        # process_pdf returns a structured object; we normalize defensively.
        result = lib.process_pdf(str(path))
        return {
            "pdf_type": getattr(result, "pdf_type", None) or getattr(result, "pdfType", "unknown"),
            "confidence": float(getattr(result, "confidence", 0.0) or 0.0),
            "page_count": int(
                getattr(result, "page_count", 0) or getattr(result, "pageCount", 0) or 0
            ),
            "pages_needing_ocr": list(
                getattr(result, "pages_needing_ocr", None)
                or getattr(result, "pagesNeedingOcr", None)
                or []
            ),
            "markdown": getattr(result, "markdown", None) if extract_markdown else None,
        }

    try:
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(_work)
            raw = future.result(timeout=timeout)
    except FuturesTimeout:
        elapsed = int((time.perf_counter() - started) * 1000)
        logger.warning(
            "pdf-inspector timed out",
            extra={"timeout_s": timeout, "path_size": path.stat().st_size},
        )
        return PdfInspectorResult(
            available=True,
            pdf_type="unknown",
            confidence=0.0,
            page_count=0,
            pages_needing_ocr=[],
            markdown=None,
            duration_ms=elapsed,
            error="timeout",
        )
    except Exception as exc:  # noqa: BLE001
        elapsed = int((time.perf_counter() - started) * 1000)
        logger.exception("pdf-inspector failed")
        return PdfInspectorResult(
            available=True,
            pdf_type="unknown",
            confidence=0.0,
            page_count=0,
            pages_needing_ocr=[],
            markdown=None,
            duration_ms=elapsed,
            error=f"{type(exc).__name__}: {exc}",
        )

    elapsed = int((time.perf_counter() - started) * 1000)
    pdf_type_raw = str(raw.get("pdf_type") or "unknown").lower().replace("-", "_")
    # Normalize common variants coming from different language bindings
    mapping = {
        "textbased": "text_based",
        "text_based": "text_based",
        "scanned": "scanned",
        "imagebased": "image_based",
        "image_based": "image_based",
        "mixed": "mixed",
    }
    pdf_type: PdfType = mapping.get(pdf_type_raw, "unknown")  # type: ignore[assignment]

    pages_needing = [int(p) for p in raw.get("pages_needing_ocr") or []]
    page_count = int(raw.get("page_count") or 0)
    # pdf-inspector 0.2.x returns 1-based page numbers; normalize to 0-based.
    pages_needing = _normalize_ocr_page_indexes(pages_needing, page_count)

    # Safety: if the document is huge we may have skipped full Markdown
    markdown = raw.get("markdown")
    if (
        markdown
        and page_count > PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT
        and extract_markdown
    ):
        # Still keep classification; drop heavy Markdown to protect memory
        markdown = None

    return PdfInspectorResult(
        available=True,
        pdf_type=pdf_type,
        confidence=float(raw.get("confidence") or 0.0),
        page_count=page_count,
        pages_needing_ocr=pages_needing,
        markdown=markdown,
        duration_ms=elapsed,
        error=None,
    )


def enrich_page_capabilities_from_inspector(
    pages: list[Any],
    inspector: PdfInspectorResult,
) -> list[Any]:
    """Overlay OCR-required flags coming from pdf-inspector onto existing PageCapability list.

    This keeps the rest of the pipeline (batch builder, OCR mode application)
    completely unchanged.
    """
    if not inspector.is_usable or not pages:
        return pages

    from page_capability import PageCapability

    ocr_set = set(inspector.pages_needing_ocr)
    enriched = []
    for page in pages:
        # page is a PageCapability dataclass or dict-like
        page_no = getattr(page, "pageNumber", None)
        if page_no is None and isinstance(page, dict):
            page_no = page.get("pageNumber")
        if page_no is None:
            enriched.append(page)
            continue

        # pdf-inspector uses 0-based page indices
        zero_based = int(page_no) - 1
        needs_ocr = zero_based in ocr_set

        if hasattr(page, "to_dict"):
            data = page.to_dict()
        elif isinstance(page, dict):
            data = dict(page)
        else:
            enriched.append(page)
            continue

        if needs_ocr and not data.get("ocrRequired"):
            data["ocrRequired"] = True
            data["whyOcrRequired"] = "pdf_inspector_signal"
            if data.get("capability") == "NATIVE_TEXT":
                data["capability"] = "MIXED_TEXT_IMAGE"
        try:
            enriched.append(PageCapability(**data))
        except TypeError:
            # Test fakes / partial objects: mutate attributes in place when possible.
            if hasattr(page, "to_dict"):
                for key, value in data.items():
                    setattr(page, key, value)
                enriched.append(page)
            else:
                enriched.append(data)

    return enriched


def health_check() -> dict[str, Any]:
    """Used by readiness probe."""
    lib = _load_library()
    return {
        "enabled": _enabled(),
        "library_loaded": lib is not None,
        "import_error": _import_error,
        "timeout_seconds": PDF_INSPECTOR_TIMEOUT_SECONDS,
        "markdown_short_circuit": os.getenv(
            "PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "true"
        ).lower()
        in {"1", "true", "yes"},
        "markdown_min_confidence": float(
            os.getenv("PDF_INSPECTOR_MARKDOWN_MIN_CONFIDENCE", "0.90")
        ),
    }
