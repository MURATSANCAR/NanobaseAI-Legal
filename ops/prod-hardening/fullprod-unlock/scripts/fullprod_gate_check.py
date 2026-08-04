#!/usr/bin/env python3
"""Automatic full-prod gate checks for document-intelligence.

Run inside or against the DI container network:

  python fullprod_gate_check.py --base-url http://127.0.0.1:8090
  python fullprod_gate_check.py --base-url http://document-intelligence:8090 \\
      --golden-report /tmp/clause-quality-report.json \\
      --soak-report /tmp/soak-report.json \\
      --rollback-result /data/nanobaseai/legal/ops/prod-hardening/fullprod-unlock/rollback/ROLLBACK_DRILL_RESULT.md
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def _get_json(url: str, timeout: float = 8.0) -> Any:
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _get_text(url: str, timeout: float = 8.0) -> str:
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def check(name: str, ok: bool, detail: str, rows: list[dict[str, Any]]) -> None:
    rows.append({"gate": name, "passed": bool(ok), "detail": detail})
    mark = "PASS" if ok else "FAIL"
    print(f"[{mark}] {name}: {detail}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--golden-report", type=Path, default=None)
    parser.add_argument("--soak-report", type=Path, default=None)
    parser.add_argument("--rollback-result", type=Path, default=None)
    parser.add_argument("--require-grafana-note", action="store_true")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    rows: list[dict[str, Any]] = []

    # 1 health
    try:
        ready = _get_json(f"{base}/health/ready")
        pi = ready.get("pdf_inspector") or {}
        check(
            "health_ready_pdf_inspector",
            ready.get("status") == "UP" and pi.get("library_loaded") is True,
            json.dumps(pi, ensure_ascii=False)[:240],
            rows,
        )
        check(
            "short_circuit_flag_on",
            pi.get("markdown_short_circuit") is True,
            f"markdown_short_circuit={pi.get('markdown_short_circuit')}",
            rows,
        )
    except Exception as exc:  # noqa: BLE001
        check("health_ready_pdf_inspector", False, str(exc), rows)
        check("short_circuit_flag_on", False, "skipped", rows)

    # 2 metrics
    try:
        metrics = _get_text(f"{base}/metrics")
        check(
            "metrics_parser_series",
            "specai_parser_path_total" in metrics,
            "specai_parser_path_total present"
            if "specai_parser_path_total" in metrics
            else "missing parser metrics",
            rows,
        )
        check(
            "metrics_short_circuit_gauge",
            "specai_parser_short_circuit_enabled" in metrics,
            "gauge present"
            if "specai_parser_short_circuit_enabled" in metrics
            else "missing gauge",
            rows,
        )
    except Exception as exc:  # noqa: BLE001
        check("metrics_parser_series", False, str(exc), rows)
        check("metrics_short_circuit_gauge", False, "skipped", rows)

    # 3 core modules importable (optional local)
    try:
        from error_to_state import resolve_error_state
        from reprocess_policy import decide_reprocess_plan

        d = resolve_error_state("PDF_ENCRYPTED")
        p = decide_reprocess_plan(force_mode="FORCE_OCR")
        check(
            "core_functions_import",
            d.terminalStatus == "MANUAL_REVIEW_REQUIRED" and p.forceMode == "FORCE_OCR",
            f"encrypted→{d.terminalStatus}, force={p.forceMode}",
            rows,
        )
    except Exception as exc:  # noqa: BLE001
        check("core_functions_import", False, str(exc), rows)

    # 4 golden report
    if args.golden_report and args.golden_report.is_file():
        report = json.loads(args.golden_report.read_text(encoding="utf-8"))
        check(
            "golden_pass_rate",
            bool(report.get("passed")) and float(report.get("passRate") or 0) >= 0.80,
            f"passRate={report.get('passRate')} docs={report.get('documentCount')}",
            rows,
        )
        # diversity hint: distinct documentIds without slot clone pattern alone
        results = report.get("results") or []
        ids = [str(r.get("documentId") or "") for r in results]
        unique = len(set(ids))
        clone_heavy = unique <= 2 and len(ids) >= 8
        check(
            "golden_diversity",
            unique >= 8 and not clone_heavy,
            f"uniqueDocumentIds={unique} total={len(ids)}",
            rows,
        )
    else:
        check("golden_pass_rate", False, "report missing", rows)
        check("golden_diversity", False, "report missing", rows)

    # 5 soak report
    if args.soak_report and args.soak_report.is_file():
        soak = json.loads(args.soak_report.read_text(encoding="utf-8"))
        success = float(soak.get("successRate") or soak.get("success_rate") or 0)
        p95 = float(
            soak.get("p95Sec")
            or soak.get("p95Seconds")
            or soak.get("p95_seconds")
            or 999
        )
        sc = float(soak.get("shortCircuitRate") or soak.get("short_circuit_rate") or 0)
        check(
            "soak_thresholds",
            success >= 0.98 and p95 < 5.0 and sc >= 0.90,
            f"success={success} p95={p95} shortCircuit={sc}",
            rows,
        )
    else:
        check("soak_thresholds", False, "report missing", rows)

    # 6 rollback note
    if args.rollback_result and args.rollback_result.is_file():
        text = args.rollback_result.read_text(encoding="utf-8")
        ok = "PASS" in text.upper() and "FALSE" in text.upper() and "TRUE" in text.upper()
        check("rollback_drill_recorded", ok, args.rollback_result.as_posix(), rows)
    else:
        check("rollback_drill_recorded", False, "result markdown missing", rows)

    passed = sum(1 for r in rows if r["passed"])
    total = len(rows)
    summary = {
        "passedCount": passed,
        "totalCount": total,
        "allPassed": passed == total,
        "gates": rows,
    }
    print(json.dumps({"passedCount": passed, "totalCount": total, "allPassed": passed == total}, indent=2))
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if summary["allPassed"] else 2


if __name__ == "__main__":
    sys.exit(main())
