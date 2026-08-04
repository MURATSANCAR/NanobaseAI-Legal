#!/usr/bin/env python3
"""Golden-set clause quality evaluation for document-intelligence.

Expected schema (expected.json):
{
  "documents": [
    {
      "documentId": "dsi-sulama-v1",
      "sourcePdf": "digital.pdf",
      "expectedClauses": [
        {"title": "1. GENEL", "contentContains": ["kapsam"], "pageStart": 1, "pageEnd": 3}
      ],
      "forbiddenPatterns": ["lorem ipsum", "\\ufffd\\ufffd\\ufffd"]
    }
  ]
}

Actual schema (actual.json): produced from live parse results / clauses endpoint.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


def _norm(text: str) -> str:
    return " ".join((text or "").casefold().split())


def _title_recall(expected: list[dict[str, Any]], actual: list[dict[str, Any]]) -> float:
    if not expected:
        return 1.0
    actual_titles = [_norm(item.get("title") or "") for item in actual]
    hits = 0
    for clause in expected:
        want = _norm(clause.get("title") or "")
        if not want:
            continue
        if any(want in title or title in want for title in actual_titles if title):
            hits += 1
    return hits / max(1, len(expected))


def _content_f1(expected: list[dict[str, Any]], actual: list[dict[str, Any]]) -> float:
    """Token-set F1 over expected contentContains phrases found in actual rawText."""
    phrases: list[str] = []
    for clause in expected:
        for phrase in clause.get("contentContains") or []:
            phrases.append(_norm(str(phrase)))
    if not phrases:
        return 1.0
    corpus = _norm(
        " ".join(
            str(item.get("rawText") or item.get("normalizedText") or "")
            for item in actual
        )
    )
    hits = sum(1 for phrase in phrases if phrase and phrase in corpus)
    precision = hits / max(1, len(phrases))
    recall = precision  # phrase checklist is the gold set
    if precision + recall == 0:
        return 0.0
    return 2 * precision * recall / (precision + recall)


def _page_overlap(expected: list[dict[str, Any]], actual: list[dict[str, Any]]) -> float:
    scored = 0
    hits = 0
    for clause in expected:
        title = _norm(clause.get("title") or "")
        if not title:
            continue
        exp_start = int(clause.get("pageStart") or 1)
        exp_end = int(clause.get("pageEnd") or exp_start)
        scored += 1
        for item in actual:
            if title not in _norm(item.get("title") or "") and _norm(
                item.get("title") or ""
            ) not in title:
                continue
            act_start = int(item.get("pageStart") or 1)
            act_end = int(item.get("pageEnd") or act_start)
            if act_end < exp_start or act_start > exp_end:
                continue
            hits += 1
            break
    return 1.0 if scored == 0 else hits / scored


def _forbidden_hits(patterns: list[str], actual: list[dict[str, Any]]) -> list[str]:
    corpus = "\n".join(
        str(item.get("rawText") or item.get("title") or "") for item in actual
    )
    found: list[str] = []
    for pattern in patterns:
        try:
            if re.search(pattern, corpus, flags=re.IGNORECASE):
                found.append(pattern)
        except re.error:
            if pattern.casefold() in corpus.casefold():
                found.append(pattern)
    return found


def evaluate_document(expected_doc: dict[str, Any], actual_doc: dict[str, Any]) -> dict[str, Any]:
    expected_clauses = expected_doc.get("expectedClauses") or []
    actual_clauses = actual_doc.get("clauses") or []
    title_recall = _title_recall(expected_clauses, actual_clauses)
    content_f1 = _content_f1(expected_clauses, actual_clauses)
    page_overlap = _page_overlap(expected_clauses, actual_clauses)
    forbidden = _forbidden_hits(expected_doc.get("forbiddenPatterns") or [], actual_clauses)
    score = (title_recall + content_f1 + page_overlap) / 3.0
    passed = score >= 0.70 and not forbidden
    return {
        "documentId": expected_doc.get("documentId"),
        "titleRecall": round(title_recall, 4),
        "contentF1": round(content_f1, 4),
        "pageOverlap": round(page_overlap, 4),
        "score": round(score, 4),
        "forbiddenHits": forbidden,
        "passed": passed,
        "expectedClauseCount": len(expected_clauses),
        "actualClauseCount": len(actual_clauses),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected", type=Path, required=True)
    parser.add_argument("--actual", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--min-pass-rate", type=float, default=0.80)
    args = parser.parse_args()

    expected = json.loads(args.expected.read_text(encoding="utf-8"))
    actual = json.loads(args.actual.read_text(encoding="utf-8"))
    actual_by_id = {
        str(doc.get("documentId")): doc for doc in (actual.get("documents") or [])
    }

    results = []
    for expected_doc in expected.get("documents") or []:
        doc_id = str(expected_doc.get("documentId"))
        actual_doc = actual_by_id.get(doc_id)
        if actual_doc is None:
            results.append(
                {
                    "documentId": doc_id,
                    "passed": False,
                    "score": 0.0,
                    "error": "missing_actual",
                }
            )
            continue
        results.append(evaluate_document(expected_doc, actual_doc))

    passed = sum(1 for item in results if item.get("passed"))
    total = len(results) or 1
    pass_rate = passed / total
    report = {
        "passRate": round(pass_rate, 4),
        "minPassRate": args.min_pass_rate,
        "passed": pass_rate >= args.min_pass_rate,
        "documentCount": len(results),
        "results": results,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
