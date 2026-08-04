"""Unit tests for pre-parse error path guards."""

from __future__ import annotations

from pathlib import Path

import pytest
from pypdf import PdfWriter

from error_path_guards import (
    GuardLimits,
    ParserGuardError,
    classify_guard_error,
    guard_before_parse,
    guard_markdown_size,
)


def _write_blank_pdf(path: Path, pages: int = 1) -> None:
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=200, height=200)
    with path.open("wb") as handle:
        writer.write(handle)


def test_guard_rejects_too_small(tmp_path: Path):
    target = tmp_path / "tiny.pdf"
    target.write_bytes(b"%PDF-1.4\n")
    with pytest.raises(ParserGuardError) as caught:
        guard_before_parse(target, limits=GuardLimits(min_bytes=64))
    assert caught.value.code == "FILE_TOO_SMALL"
    payload = classify_guard_error(caught.value)
    assert payload["manualReview"] is True
    assert payload["errorCode"] == "FILE_TOO_SMALL"


def test_guard_rejects_not_a_pdf(tmp_path: Path):
    target = tmp_path / "nope.bin"
    target.write_bytes(b"NOTPDF" + b"x" * 100)
    with pytest.raises(ParserGuardError) as caught:
        guard_before_parse(target)
    assert caught.value.code == "NOT_A_PDF"


def test_guard_rejects_zero_and_too_many_pages(tmp_path: Path):
    empty = tmp_path / "empty.pdf"
    writer = PdfWriter()
    with empty.open("wb") as handle:
        writer.write(handle)
    with pytest.raises(ParserGuardError) as caught:
        guard_before_parse(empty)
    assert caught.value.code in {"ZERO_PAGES", "PDF_CORRUPT", "NOT_A_PDF", "FILE_TOO_SMALL"}

    many = tmp_path / "many.pdf"
    _write_blank_pdf(many, pages=3)
    with pytest.raises(ParserGuardError) as caught:
        guard_before_parse(many, limits=GuardLimits(max_pages=2))
    assert caught.value.code == "TOO_MANY_PAGES"
    assert classify_guard_error(caught.value)["terminalStatus"] == "FAILED"


def test_guard_accepts_valid_pdf_and_markdown_drop(tmp_path: Path):
    ok = tmp_path / "ok.pdf"
    _write_blank_pdf(ok, pages=2)
    meta = guard_before_parse(ok)
    assert meta["pageCount"] == 2
    assert guard_markdown_size("hello") == "hello"
    assert guard_markdown_size("x" * 100, limits=GuardLimits(max_markdown_chars=10)) is None
