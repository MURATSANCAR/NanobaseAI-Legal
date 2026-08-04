"""Unit / contract tests for the pdf-inspector bridge.

These tests run without the native library installed (they exercise the
fallback paths). When pdf-inspector is present, additional live tests can
be enabled with the environment variable PDF_INSPECTOR_LIVE=1.
"""

from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from pdf_inspector_bridge import (
    PdfInspectorResult,
    enrich_page_capabilities_from_inspector,
    health_check,
    inspect_pdf,
)


def test_health_check_returns_structure():
    h = health_check()
    assert "enabled" in h
    assert "library_loaded" in h
    assert isinstance(h["enabled"], bool)


def test_inspect_disabled_by_config(monkeypatch):
    monkeypatch.setenv("PDF_INSPECTOR_ENABLED", "false")
    result = inspect_pdf("/tmp/does-not-exist-12345.pdf")
    assert isinstance(result, PdfInspectorResult)
    assert result.available is False
    assert result.error == "disabled_by_config"


def test_inspect_missing_file(monkeypatch):
    monkeypatch.setenv("PDF_INSPECTOR_ENABLED", "true")
    result = inspect_pdf(Path("/tmp/nanobase-missing-file-xyz.pdf"))
    assert result.error in {"file_not_found", "import_failed", "disabled_by_config"}
    assert result.markdown is None
    assert result.page_count == 0


def test_result_digital_text_ratio():
    r = PdfInspectorResult(
        available=True,
        pdf_type="text_based",
        confidence=0.92,
        page_count=10,
        pages_needing_ocr=[],
        markdown="# Hello",
        duration_ms=42,
    )
    assert r.is_usable is True
    assert r.digital_text_ratio_estimate == pytest.approx(0.95)

    r2 = PdfInspectorResult(
        available=True,
        pdf_type="mixed",
        confidence=0.80,
        page_count=10,
        pages_needing_ocr=[1, 3, 5],
        markdown=None,
        duration_ms=50,
    )
    assert r2.digital_text_ratio_estimate == pytest.approx(0.7)


def test_enrich_page_capabilities_overlays_ocr_flag():
    from page_capability import classify_page_signals

    pages = [
        classify_page_signals(
            page_number=1,
            text="native paragraph one\nnative paragraph two\nnative paragraph three",
            font_count=2,
        ),
        classify_page_signals(
            page_number=2,
            text="native paragraph one\nnative paragraph two\nnative paragraph three",
            font_count=2,
        ),
        classify_page_signals(
            page_number=3,
            text="native paragraph one\nnative paragraph two\nnative paragraph three",
            font_count=2,
        ),
    ]
    assert pages[1].ocrRequired is False
    inspector = PdfInspectorResult(
        available=True,
        pdf_type="mixed",
        confidence=0.88,
        page_count=3,
        pages_needing_ocr=[1],  # 0-based → page 2
        markdown=None,
        duration_ms=30,
    )
    enriched = enrich_page_capabilities_from_inspector(pages, inspector)
    assert len(enriched) == 3
    assert enriched[1].ocrRequired is True
    assert enriched[1].whyOcrRequired == "pdf_inspector_signal"
    assert enriched[1].capability == "MIXED_TEXT_IMAGE"


def test_enrich_softens_ocr_flags_for_high_conf_text_based():
    from page_capability import classify_page_signals
    from pdf_inspector_bridge import decide_ocr_mode

    pages = [
        classify_page_signals(
            page_number=i,
            text="native paragraph one\nnative paragraph two\nnative paragraph three",
            font_count=2,
        )
        for i in range(1, 4)
    ]
    inspector = PdfInspectorResult(
        available=True,
        pdf_type="text_based",
        confidence=1.0,
        page_count=3,
        pages_needing_ocr=[0, 1, 2],
        markdown="",
        duration_ms=17,
    )
    enriched = enrich_page_capabilities_from_inspector(pages, inspector)
    assert all(p.ocrRequired is False for p in enriched)
    assert all(p.capability == "NATIVE_TEXT" for p in enriched)
    assert decide_ocr_mode(inspector) == "AUTO"


def test_decide_ocr_mode_matrix():
    from pdf_inspector_bridge import decide_ocr_mode

    assert (
        decide_ocr_mode(
            PdfInspectorResult(
                available=True,
                pdf_type="text_based",
                confidence=1.0,
                page_count=1,
                pages_needing_ocr=[],
                markdown="# hi",
                duration_ms=1,
            )
        )
        == "DISABLED"
    )
    assert (
        decide_ocr_mode(
            PdfInspectorResult(
                available=True,
                pdf_type="scanned",
                confidence=0.99,
                page_count=1,
                pages_needing_ocr=[0],
                markdown=None,
                duration_ms=1,
            )
        )
        == "FORCED"
    )


def test_normalize_ocr_page_indexes_one_based():
    from pdf_inspector_bridge import _normalize_ocr_page_indexes

    assert _normalize_ocr_page_indexes([1, 2, 3], 3) == [0, 1, 2]
    assert _normalize_ocr_page_indexes([0, 1], 3) == [0, 1]
    assert _normalize_ocr_page_indexes([], 3) == []


def test_text_based_fast_path_builds_native_pages():
    from page_capability_pdf_inspector import _build_native_pages_from_inspector

    inspector = PdfInspectorResult(
        available=True,
        pdf_type="text_based",
        confidence=1.0,
        page_count=3,
        pages_needing_ocr=[],
        markdown="# hello",
        duration_ms=10,
    )
    pages = _build_native_pages_from_inspector(inspector, native_min_chars=40)
    assert len(pages) == 3
    assert all(p.capability == "NATIVE_TEXT" for p in pages)
    assert all(p.ocrRequired is False for p in pages)


def test_text_based_empty_markdown_prefers_docling_native_capability():
    from page_capability_pdf_inspector import _build_native_pages_from_inspector
    from page_capability import build_batches

    inspector = PdfInspectorResult(
        available=True,
        pdf_type="text_based",
        confidence=1.0,
        page_count=2,
        pages_needing_ocr=[0, 1],
        markdown="",
        duration_ms=10,
    )
    pages = _build_native_pages_from_inspector(inspector, native_min_chars=40)
    assert all(p.capability == "VECTOR_COMPLEX" for p in pages)
    assert all(p.ocrRequired is False for p in pages)
    batches = build_batches(pages, batch_size=5)
    assert batches[0]["route"] == "DOCLING"
    assert batches[0]["ocrRequired"] is False


@pytest.mark.skipif(
    os.getenv("PDF_INSPECTOR_LIVE") != "1",
    reason="Set PDF_INSPECTOR_LIVE=1 and provide a real PDF to run live test",
)
def test_live_inspect_on_real_pdf():
    path = Path(os.getenv("PDF_INSPECTOR_TEST_PDF", "/tmp/specai-test.pdf"))
    if not path.exists():
        pytest.skip("test PDF not present")
    result = inspect_pdf(path)
    assert result.available is True
    assert result.error is None
    assert result.page_count >= 1
    assert result.pdf_type in {"text_based", "scanned", "mixed", "image_based"}
