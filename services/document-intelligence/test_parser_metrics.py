"""Unit / contract tests for parser Prometheus metrics helpers."""

from __future__ import annotations

import parser_metrics as pm


def test_track_parse_success_records_without_raising():
    with pm.track_parse("pdf_inspector_short_circuit", "text_based") as mctx:
        mctx["page_count"] = 25
        mctx["clause_count"] = 34
        mctx["table_count"] = 22
        mctx["outcome"] = "success"
    # No exception = pass (works with or without prometheus_client)


def test_track_parse_error_outcome_on_exception():
    raised = False
    try:
        with pm.track_parse("docling", "scanned") as mctx:
            mctx["page_count"] = 3
            raise RuntimeError("boom")
    except RuntimeError:
        raised = True
    assert raised is True


def test_record_parse_accepts_all_path_labels():
    for path in (
        "pdf_inspector_short_circuit",
        "docling",
        "legacy",
        "fallback",
    ):
        pm.record_parse(
            path,
            "mixed",
            0.12,
            page_count=2,
            clause_count=1,
            table_count=0,
            outcome="success",
        )


def test_metrics_payload_returns_bytes():
    body, content_type = pm.metrics_payload()
    assert isinstance(body, (bytes, bytearray))
    assert "text/" in content_type
    if pm.prometheus_available():
        assert b"specai_parser_path_total" in body or b"specai_parser_" in body
        assert b"specai_parser_short_circuit_enabled" in body
    else:
        assert b"specai_parser_metrics_available" in body
