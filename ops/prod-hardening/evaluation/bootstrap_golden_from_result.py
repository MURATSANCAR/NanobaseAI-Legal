#!/usr/bin/env python3
"""Bootstrap golden expected/actual from a live DI short-circuit result.

Usage (inside DI container network):
  python bootstrap_golden_from_result.py --result-json /tmp/result.json --document-id dsi-sulama-v1
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result-json", type=Path, required=True)
    parser.add_argument("--document-id", required=True)
    parser.add_argument("--source-pdf", default="digital.pdf")
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--max-expected-clauses", type=int, default=12)
    args = parser.parse_args()

    result = json.loads(args.result_json.read_text(encoding="utf-8"))
    clauses = result.get("clauses") or []
    actual_doc = {
        "documentId": args.document_id,
        "sourcePdf": args.source_pdf,
        "pageCount": result.get("pageCount"),
        "provider": result.get("provider"),
        "shortCircuited": (result.get("metadata") or {}).get("shortCircuited"),
        "clauses": [
            {
                "title": c.get("title"),
                "rawText": c.get("rawText"),
                "pageStart": c.get("pageStart"),
                "pageEnd": c.get("pageEnd"),
                "clauseNumber": c.get("clauseNumber"),
            }
            for c in clauses
        ],
    }
    expected_clauses = []
    for clause in clauses[: args.max_expected_clauses]:
        title = (clause.get("title") or "").strip()
        raw = (clause.get("rawText") or "").strip()
        if not title:
            continue
        tokens = [t for t in raw.split() if len(t) > 4][:3]
        expected_clauses.append(
            {
                "title": title,
                "contentContains": tokens,
                "pageStart": clause.get("pageStart") or 1,
                "pageEnd": clause.get("pageEnd") or 1,
            }
        )
    expected_doc = {
        "documentId": args.document_id,
        "sourcePdf": args.source_pdf,
        "expectedClauses": expected_clauses,
        "forbiddenPatterns": ["lorem ipsum", "\ufffd\ufffd\ufffd"],
    }
    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "actual.json").write_text(
        json.dumps({"documents": [actual_doc]}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (args.out_dir / "expected.json").write_text(
        json.dumps({"documents": [expected_doc]}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"wrote {args.out_dir}/expected.json and actual.json ({len(expected_clauses)} expected clauses)")


if __name__ == "__main__":
    main()
