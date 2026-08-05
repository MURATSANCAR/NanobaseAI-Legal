"""Pure-function tests for D/A/B/C core modules."""

from __future__ import annotations

from error_to_state import build_audit_event, resolve_error_state
from markdown_clause_parser import parse_markdown_clauses
from reprocess_policy import apply_plan_to_parse_options, decide_reprocess_plan
from requirement_from_clauses import (
    attach_requirements_to_result,
    requirements_from_clauses,
)


def test_markdown_parser_madde_and_hierarchy():
    md = """# 1. Genel
Kapsam metni.

## Madde 1.1 – Tanımlar
Tanım satırı.

Madde 2: Yükümlülükler
Yüklenici zorunlu olarak hizmeti sağlayacaktır.

| A | B |
|---|---|
| 1 | 2 |
"""
    clauses, tables = parse_markdown_clauses(md, page_count=3)
    assert len(tables) == 1
    assert any(c["clauseType"] == "MADDE" for c in clauses)
    assert any(c.get("parentSourceId") for c in clauses)
    ids = [c["sourceId"] for c in clauses]
    assert len(ids) == len(set(ids))
    # Table rows must not appear as clause titles
    assert all("|" not in (c.get("title") or "") for c in clauses)


def test_markdown_parser_stable_ids():
    md = "# Madde 5 – Güvenlik\nMetin.\n"
    a, _ = parse_markdown_clauses(md)
    b, _ = parse_markdown_clauses(md)
    assert a[0]["sourceId"] == b[0]["sourceId"]


def test_requirements_must_should_and_source_ids():
    clauses = [
        {
            "sourceId": "md-a",
            "title": "SLA",
            "rawText": "Yüklenici 7/24 destek sağlamak zorundadır. Kesinti süresi SLA kapsamındadır.",
            "pageStart": 2,
            "pageEnd": 2,
            "clauseNumber": "3.1",
        },
        {
            "sourceId": "md-b",
            "title": "Tercih",
            "rawText": "Sistem tercihen bulut üzerinde çalıştırılabilir.",
            "pageStart": 3,
            "pageEnd": 3,
            "clauseNumber": "4",
        },
    ]
    reqs = requirements_from_clauses(clauses)
    assert len(reqs) >= 1
    must = next(r for r in reqs if r["sourceClauseIds"] == ["md-a"])
    assert must["obligationLevel"] == "MUST"
    assert must["category"] in {"OPERATIONAL", "TECHNICAL", "SCHEDULE", "OTHER"}


def test_attach_requirements_to_result():
    result = {
        "clauses": [
            {
                "sourceId": "c1",
                "title": "Ödeme",
                "rawText": "Ödeme bedeli sözleşme ile zorunlu olarak belirlenecektir.",
                "pageStart": 1,
                "pageEnd": 1,
            }
        ],
        "metadata": {"terminalStatus": "READY"},
    }
    enriched = attach_requirements_to_result(result)
    assert "requirements" in enriched
    assert enriched["metadata"]["requirementCount"] == len(enriched["requirements"])
    assert result.get("requirements") is None  # non-destructive on original ref content ok if copy


def test_error_encrypted_maps_to_manual_review():
    decision = resolve_error_state("PDF_ENCRYPTED")
    assert decision.terminalStatus == "MANUAL_REVIEW_REQUIRED"
    assert decision.manualReview is True
    assert decision.portalMessageKey == "document.error.encrypted"
    audit = build_audit_event(decision, job_id="j1", document_version_id="v1")
    assert audit["action"] == "PARSER_ROUTED_TO_MANUAL_REVIEW"
    assert "rawText" not in audit


def test_error_too_large_maps_to_failed():
    decision = resolve_error_state("FILE_TOO_LARGE")
    assert decision.terminalStatus == "FAILED"
    assert decision.manualReview is False


def test_reprocess_force_modes():
    ocr = decide_reprocess_plan(force_mode="FORCE_OCR")
    assert ocr.ocrMode == "FORCED"
    assert ocr.allowShortCircuit is False
    assert ocr.createNewJob is True
    assert ocr.preservePreviousResult is True

    sc = decide_reprocess_plan(force_mode="FORCE_SHORT_CIRCUIT")
    assert sc.allowShortCircuit is True
    assert sc.preferDocling is False

    docling = decide_reprocess_plan(force_mode="FORCE_DOCLING")
    assert docling.allowShortCircuit is False
    assert docling.preferDocling is True

    opts = apply_plan_to_parse_options(ocr)
    assert opts["ocrMode"] == "FORCED"


def test_reprocess_auto_after_encrypted():
    plan = decide_reprocess_plan(
        force_mode="AUTO",
        previous_error_code="PDF_ENCRYPTED",
    )
    assert plan.allowShortCircuit is False
    assert plan.preferDocling is True
