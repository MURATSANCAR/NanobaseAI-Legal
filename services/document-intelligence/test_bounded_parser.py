"""Unit tests for bounded parser routing primitives."""

from __future__ import annotations

import json
import threading
import uuid
from pathlib import Path

import pytest

from page_capability import build_batches, classify_page_signals
from parser_checkpoint import ParserCheckpointStore
from bounded_parser import merge_batch_results, remap_page_numbers, BoundedParseError


def test_page_capability_native_text():
    page = classify_page_signals(
        page_number=1,
        text="Başlık\nMadde 1. Yüklenici hizmet verecektir.\nMadde 2. SLA uygulanır.",
        image_count=0,
        font_count=2,
    )
    assert page.capability == "NATIVE_TEXT"
    assert page.ocrRequired is False


def test_page_capability_scanned_requires_ocr():
    page = classify_page_signals(
        page_number=2,
        text="",
        image_count=1,
    )
    assert page.capability == "SCANNED_IMAGE"
    assert page.ocrRequired is True
    assert page.whyOcrRequired


def test_selective_ocr_and_table_routing_batches():
    native = (
        "Başlık satırı\n"
        "Yüklenici bu maddede belirtilen hizmetleri eksiksiz sunacaktır. "
        "SLA ve cezai şart uygulanır."
    )
    pages = [
        classify_page_signals(page_number=1, text=native, image_count=0),
        classify_page_signals(page_number=2, text=native, image_count=0),
        classify_page_signals(page_number=3, text="", image_count=1),
        classify_page_signals(page_number=4, text="", image_count=1),
        classify_page_signals(
            page_number=5,
            text="col1|col2|col3\n1|2|3\n4|5|6\n7|8|9\nenough native chars for gate XX",
            image_count=0,
        ),
    ]
    assert pages[0].capability == "NATIVE_TEXT"
    assert pages[0].ocrRequired is False
    batches = build_batches(pages, batch_size=2)
    assert batches[0]["route"] == "NATIVE"
    assert batches[0]["pageNumbers"] == [1, 2]
    assert batches[1]["route"] == "OCR"
    assert batches[1]["pageNumbers"] == [3, 4]
    assert any(batch["route"] == "TABLE" for batch in batches)


def test_checkpoint_idempotency(tmp_path: Path):
    store = ParserCheckpointStore(tmp_path / "jobs.sqlite3", threading.Lock())
    store.initialize()
    job_id = str(uuid.uuid4())
    payload = {
        "id": str(uuid.uuid4()),
        "document_version_id": str(uuid.uuid4()),
        "parser_job_id": job_id,
        "page_number": 1,
        "batch_number": 1,
        "provider": "DOCLING",
        "status": "SUCCEEDED",
        "attempt": 1,
        "ocr_used": False,
        "result": {"pages": [{"pageNumber": 1, "rawText": "hello", "textQualityScore": 0.5}]},
    }
    store.save(payload)
    store.save({**payload, "id": str(uuid.uuid4()), "result": {"pages": [{"pageNumber": 1, "rawText": "hello", "textQualityScore": 0.5}]}})
    latest = store.latest_success_by_batch(job_id)
    assert 1 in latest
    assert latest[1]["result"]["pages"][0]["rawText"] == "hello"


def test_merge_rejects_missing_and_duplicate_pages():
    pages = [
        {
            "provider": "DOCLING",
            "pages": [
                {"pageNumber": 1, "rawText": "a", "textQualityScore": 0.8},
                {"pageNumber": 2, "rawText": "b", "textQualityScore": 0.8},
            ],
            "clauses": [],
            "tables": [],
            "warnings": [],
        }
    ]
    merged = merge_batch_results(
        str(uuid.uuid4()),
        "tr",
        pages,
        expected_page_count=2,
        plan={"checkpointCount": 1},
    )
    assert merged["metadata"]["terminalStatus"] == "READY"
    assert merged["metadata"]["qualityGate"] == "PASS"

    with pytest.raises(BoundedParseError) as missing:
        merge_batch_results(
            str(uuid.uuid4()),
            "tr",
            pages,
            expected_page_count=3,
            plan={"checkpointCount": 1},
        )
    assert missing.value.code == "MISSING_PAGES"

    duplicate = [
        pages[0],
        {
            "provider": "DOCLING",
            "pages": [{"pageNumber": 1, "rawText": "dup", "textQualityScore": 0.1}],
            "clauses": [],
            "tables": [],
            "warnings": [],
        },
    ]
    with pytest.raises(BoundedParseError) as dup:
        merge_batch_results(
            str(uuid.uuid4()),
            "tr",
            duplicate,
            expected_page_count=2,
            plan={"checkpointCount": 1},
        )
    assert dup.value.code == "DUPLICATE_PAGE_BLOCK"


def test_remap_page_numbers_when_docling_resets_range():
    result = {
        "pages": [{"pageNumber": 1, "rawText": "x"}],
        "clauses": [{"pageStart": 1, "pageEnd": 1, "boundingBoxes": [{"page": 1}]}],
        "tables": [],
        "warnings": [{"pageNumber": 1}],
    }
    remapped = remap_page_numbers(result, page_start=11)
    assert remapped["pages"][0]["pageNumber"] == 11
    assert remapped["clauses"][0]["pageStart"] == 11


def test_timeout_error_taxonomy_codes():
    assert BoundedParseError("BATCH_TIMEOUT", "x").code == "BATCH_TIMEOUT"
    assert BoundedParseError("DOCUMENT_ORCHESTRATION_TIMEOUT", "x").code == (
        "DOCUMENT_ORCHESTRATION_TIMEOUT"
    )
