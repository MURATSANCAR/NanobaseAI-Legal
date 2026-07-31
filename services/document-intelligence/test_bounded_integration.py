"""Integration-style tests for bounded parsing with mocked provider execution."""

from __future__ import annotations

import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from pypdf import PdfWriter


def _write_blank_pdf(path: Path, pages: int = 2) -> None:
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=612, height=792)
    with path.open("wb") as handle:
        writer.write(handle)


def test_native_fast_path_and_checkpoint_reuse(tmp_path, monkeypatch):
    import bounded_parser as bp
    from parser_checkpoint import ParserCheckpointStore

    pdf_path = tmp_path / "native.pdf"
    _write_blank_pdf(pdf_path, pages=2)

    fake_caps = []
    from page_capability import classify_page_signals

    for number in (1, 2):
        fake_caps.append(
            classify_page_signals(
                page_number=number,
                text="Madde 1\nYüklenici hizmet verecektir ve SLA uygulanır. " * 3,
                image_count=0,
                font_count=1,
            )
        )

    monkeypatch.setattr(bp, "classify_pdf_pages", lambda *_args, **_kwargs: fake_caps)

    def fake_native(path, page_numbers, request, *, capabilities):
        pages = []
        for page_number in page_numbers:
            text = "Madde 1\nYüklenici hizmet verecektir ve SLA uygulanır. " * 3
            pages.append(
                {
                    "pageNumber": page_number,
                    "width": 612,
                    "height": 792,
                    "rotation": 0,
                    "rawText": text,
                    "normalizedText": " ".join(text.split()),
                    "textQualityScore": 0.9,
                    "thumbnailObjectKey": None,
                    "metadata": {"provider": "NATIVE_TEXT_PROVIDER"},
                }
            )
        return {
            "documentVersionId": str(request.documentVersionId),
            "provider": "NATIVE_TEXT_PROVIDER",
            "providerVersion": "test",
            "pageCount": len(pages),
            "language": "tr",
            "textQualityScore": 0.9,
            "pages": pages,
            "clauses": [],
            "tables": [],
            "warnings": [],
            "metadata": {"route": "NATIVE"},
        }

    monkeypatch.setattr(bp, "native_pages_result", fake_native)

    class Req:
        documentVersionId = uuid.uuid4()
        mimeType = "application/pdf"
        languageHint = "tr"
        ocrMode = "AUTO"
        extractTables = True

    store = ParserCheckpointStore(tmp_path / "jobs.sqlite3", threading.Lock())
    store.initialize()
    job_id = str(uuid.uuid4())
    events = []

    with ThreadPoolExecutor(max_workers=1) as executor:
        first = bp.run_bounded_parse(
            path=pdf_path,
            request=Req(),
            job_id=job_id,
            checkpoint_store=store,
            executor=executor,
            on_progress=events.append,
            is_cancelled=lambda: False,
        )
    assert first["metadata"]["terminalStatus"] == "READY"
    assert first["pageCount"] == 2
    assert first["metadata"]["checkpointCount"] >= 1
    assert set(first["metadata"]["parserPlan"]["nativeTextPages"]) == {1, 2}
    assert first["metadata"]["parserPlan"]["ocrPages"] == []

    with ThreadPoolExecutor(max_workers=1) as executor:
        second = bp.run_bounded_parse(
            path=pdf_path,
            request=Req(),
            job_id=job_id,
            checkpoint_store=store,
            executor=executor,
            on_progress=events.append,
            is_cancelled=lambda: False,
        )
    assert second["metadata"]["terminalStatus"] == "READY"
    assert second["metadata"]["checkpointCount"] >= 1


def test_mixed_pdf_routes_ocr_only_where_needed(tmp_path, monkeypatch):
    import bounded_parser as bp
    from page_capability import classify_page_signals
    from parser_checkpoint import ParserCheckpointStore

    pdf_path = tmp_path / "mixed.pdf"
    _write_blank_pdf(pdf_path, pages=2)
    caps = [
        classify_page_signals(
            page_number=1,
            text="Native heading\nBody obligation text that is long enough.",
            image_count=0,
        ),
        classify_page_signals(page_number=2, text="", image_count=1),
    ]
    monkeypatch.setattr(bp, "classify_pdf_pages", lambda *_a, **_k: caps)

    def fake_native(path, page_numbers, request, *, capabilities):
        return {
            "documentVersionId": str(request.documentVersionId),
            "provider": "NATIVE_TEXT_PROVIDER",
            "providerVersion": "test",
            "pageCount": len(page_numbers),
            "language": "tr",
            "textQualityScore": 0.8,
            "pages": [
                {
                    "pageNumber": number,
                    "width": 1,
                    "height": 1,
                    "rotation": 0,
                    "rawText": "native page text content for quality",
                    "normalizedText": "native page text content for quality",
                    "textQualityScore": 0.8,
                    "thumbnailObjectKey": None,
                    "metadata": {},
                }
                for number in page_numbers
            ],
            "clauses": [],
            "tables": [],
            "warnings": [],
            "metadata": {},
        }

    def fake_docling(path, request, *, page_start, page_end, ocr_required, extract_tables):
        assert ocr_required is True
        assert page_start == page_end == 2
        return {
            "documentVersionId": str(request.documentVersionId),
            "provider": "DOCLING",
            "providerVersion": "test",
            "pageCount": 1,
            "language": "tr",
            "textQualityScore": 0.7,
            "pages": [
                {
                    "pageNumber": 2,
                    "width": 1,
                    "height": 1,
                    "rotation": 0,
                    "rawText": "ocr recovered text for scanned page",
                    "normalizedText": "ocr recovered text for scanned page",
                    "textQualityScore": 0.7,
                    "thumbnailObjectKey": None,
                    "metadata": {},
                }
            ],
            "clauses": [],
            "tables": [],
            "warnings": [],
            "metadata": {"ocrUsed": True},
        }

    monkeypatch.setattr(bp, "native_pages_result", fake_native)
    monkeypatch.setattr(bp, "convert_docling_batch", fake_docling)

    class Req:
        documentVersionId = uuid.uuid4()
        mimeType = "application/pdf"
        languageHint = "tr"
        ocrMode = "AUTO"
        extractTables = True

    store = ParserCheckpointStore(tmp_path / "jobs.sqlite3", threading.Lock())
    store.initialize()
    with ThreadPoolExecutor(max_workers=1) as executor:
        result = bp.run_bounded_parse(
            path=pdf_path,
            request=Req(),
            job_id=str(uuid.uuid4()),
            checkpoint_store=store,
            executor=executor,
            on_progress=lambda _event: None,
            is_cancelled=lambda: False,
        )
    plan = result["metadata"]["parserPlan"]
    assert plan["nativeTextPages"] == [1]
    assert plan["ocrPages"] == [2]
    assert result["pageCount"] == 2


def test_batch_failure_then_page_retry(tmp_path, monkeypatch):
    import bounded_parser as bp
    from concurrent.futures import TimeoutError as FuturesTimeout
    from page_capability import classify_page_signals
    from parser_checkpoint import ParserCheckpointStore

    pdf_path = tmp_path / "scan.pdf"
    _write_blank_pdf(pdf_path, pages=2)
    caps = [
        classify_page_signals(page_number=1, text="", image_count=1),
        classify_page_signals(page_number=2, text="", image_count=1),
    ]
    monkeypatch.setattr(bp, "classify_pdf_pages", lambda *_a, **_k: caps)
    monkeypatch.setattr(bp, "BATCH_SIZE", 2)
    calls = {"n": 0}

    def flaky_docling(path, request, *, page_start, page_end, ocr_required, extract_tables):
        calls["n"] += 1
        if page_end > page_start:
            raise FuturesTimeout()
        return {
            "documentVersionId": str(request.documentVersionId),
            "provider": "DOCLING",
            "providerVersion": "test",
            "pageCount": 1,
            "language": "tr",
            "textQualityScore": 0.6,
            "pages": [
                {
                    "pageNumber": page_start,
                    "width": 1,
                    "height": 1,
                    "rotation": 0,
                    "rawText": f"page {page_start} text",
                    "normalizedText": f"page {page_start} text",
                    "textQualityScore": 0.6,
                    "thumbnailObjectKey": None,
                    "metadata": {},
                }
            ],
            "clauses": [],
            "tables": [],
            "warnings": [],
            "metadata": {},
        }

    monkeypatch.setattr(bp, "convert_docling_batch", flaky_docling)

    class Req:
        documentVersionId = uuid.uuid4()
        mimeType = "application/pdf"
        languageHint = "tr"
        ocrMode = "AUTO"
        extractTables = False

    store = ParserCheckpointStore(tmp_path / "jobs.sqlite3", threading.Lock())
    store.initialize()
    with ThreadPoolExecutor(max_workers=1) as executor:
        result = bp.run_bounded_parse(
            path=pdf_path,
            request=Req(),
            job_id=str(uuid.uuid4()),
            checkpoint_store=store,
            executor=executor,
            on_progress=lambda _event: None,
            is_cancelled=lambda: False,
        )
    assert result["pageCount"] == 2
    assert calls["n"] >= 3
    assert result["metadata"]["parserPlan"]["fallbackPages"]


def test_cancellation_aborts(tmp_path, monkeypatch):
    import bounded_parser as bp
    from page_capability import classify_page_signals
    from parser_checkpoint import ParserCheckpointStore

    pdf_path = tmp_path / "x.pdf"
    _write_blank_pdf(pdf_path, pages=1)
    monkeypatch.setattr(
        bp,
        "classify_pdf_pages",
        lambda *_a, **_k: [classify_page_signals(page_number=1, text="", image_count=1)],
    )

    class Req:
        documentVersionId = uuid.uuid4()
        mimeType = "application/pdf"
        languageHint = "tr"
        ocrMode = "AUTO"
        extractTables = False

    store = ParserCheckpointStore(tmp_path / "jobs.sqlite3", threading.Lock())
    store.initialize()
    with ThreadPoolExecutor(max_workers=1) as executor:
        with pytest.raises(bp.BoundedParseError) as raised:
            bp.run_bounded_parse(
                path=pdf_path,
                request=Req(),
                job_id=str(uuid.uuid4()),
                checkpoint_store=store,
                executor=executor,
                on_progress=lambda _event: None,
                is_cancelled=lambda: True,
            )
    assert raised.value.code == "CANCELLED"
