"""Unit tests for Markdown short-circuit routing."""

from __future__ import annotations

from types import SimpleNamespace

from markdown_short_circuit import run_markdown_short_circuit, should_short_circuit
from pdf_inspector_bridge import PdfInspectorResult


def _inspector(**overrides):
    base = dict(
        available=True,
        pdf_type="text_based",
        confidence=0.95,
        page_count=2,
        pages_needing_ocr=[],
        markdown="# 1. Genel\nMetin satırı bir.\n\n# 2. Teknik\nMetin satırı iki.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n",
        duration_ms=12,
        error=None,
    )
    base.update(overrides)
    return PdfInspectorResult(**base)


def test_should_short_circuit_accepts_high_confidence_text_based(monkeypatch):
    monkeypatch.setenv("PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "true")
    monkeypatch.setenv("PDF_INSPECTOR_MARKDOWN_MIN_CONFIDENCE", "0.90")
    assert should_short_circuit(_inspector()) is True


def test_should_short_circuit_rejects_scanned_and_forced(monkeypatch):
    monkeypatch.setenv("PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "true")
    assert should_short_circuit(_inspector(pdf_type="scanned")) is False
    assert should_short_circuit(_inspector(), ocr_mode="FORCED") is False
    assert should_short_circuit(_inspector(confidence=0.5)) is False
    assert should_short_circuit(_inspector(markdown=None)) is False


def test_should_short_circuit_disabled(monkeypatch):
    monkeypatch.setenv("PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "false")
    assert should_short_circuit(_inspector()) is False


def test_run_markdown_short_circuit_schema():
    events = []
    result = run_markdown_short_circuit(
        document_version_id="11111111-1111-1111-1111-111111111111",
        inspector_result=_inspector(),
        language="tr",
        progress_callback=events.append,
    )
    assert result["provider"] == "PDF_INSPECTOR"
    assert result["pageCount"] == 2
    assert result["metadata"]["shortCircuited"] is True
    assert result["metadata"]["qualityGate"] == "PASS"
    assert result["metadata"]["layoutBlockCount"] >= 1
    assert len(result["pages"]) == 2
    assert all((page.get("rawText") or "").strip() for page in result["pages"])
    assert len(result["clauses"]) >= 2
    assert result["clauses"][0]["contentHash"]
    assert result["clauses"][0]["rawText"]
    assert len(result["tables"]) >= 1
    assert result["tables"][0]["markdownContent"].startswith("|")
    assert any(e.get("currentStage") == "MARKDOWN_SHORT_CIRCUIT" for e in events)
    assert any(e.get("currentStage") == "MARKDOWN_SHORT_CIRCUIT_DONE" for e in events)


def test_run_markdown_short_circuit_without_headings():
    result = run_markdown_short_circuit(
        document_version_id="11111111-1111-1111-1111-111111111111",
        inspector_result=_inspector(
            markdown="Düz metin başlıksız paragraf. " * 20,
            page_count=1,
        ),
    )
    assert len(result["clauses"]) == 1
    assert result["clauses"][0]["metadata"].get("fallback") is True
