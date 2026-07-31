#!/usr/bin/env python3
"""v1.1 corpus E2E runner.

Runs only fixtures that intake classifies as READY_FOR_SMOKE or READY_FOR_QUALITY_GATE.
Missing assets produce SKIPPED — never fabricated PASS.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SLICE_ALIASES = {
    "native-pdf": {"NATIVE_TEXT"},
    "scanned-pdf": {"SCANNED_IMAGE"},
    "docx": {"DOCX_STRUCTURED"},
    "table-heavy": {"TABLE_DOMINANT"},
    "certificate": {"CERTIFICATE"},
    "datasheet": {"PRODUCT_DATASHEET"},
    "knowledge": {"CERTIFICATE", "PRODUCT_DATASHEET", "QUALITY_CERTIFICATE"},
    "report-regression": set(),  # handled separately / policy later
    "all": None,
}


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def run_intake(manifest_dir: Path, asset_root: Path, out_dir: Path) -> dict[str, Any]:
    intake_out = out_dir / "intake-report.json"
    inventory_out = out_dir / "corpus-inventory.json"
    cmd = [
        sys.executable,
        str(ROOT / "scripts/corpus_intake.py"),
        "--manifest-dir", str(manifest_dir),
        "--asset-root", str(asset_root),
        "--output", str(intake_out),
        "--inventory-output", str(inventory_out),
    ]
    subprocess.check_call(cmd)
    return json.loads(intake_out.read_text(encoding="utf-8"))


def matches_slice(manifest_slice: dict[str, Any], slice_name: str) -> bool:
    if slice_name == "all":
        return True
    if slice_name == "report-regression":
        return False
    wanted = SLICE_ALIASES.get(slice_name)
    if wanted is None:
        return True
    purpose = (manifest_slice or {}).get("documentPurpose")
    mode = (manifest_slice or {}).get("contentMode")
    fmt = (manifest_slice or {}).get("format")
    if slice_name == "certificate":
        return purpose == "CERTIFICATE"
    if slice_name == "datasheet":
        return purpose == "PRODUCT_DATASHEET"
    if slice_name == "knowledge":
        return purpose in wanted
    if slice_name == "docx":
        return fmt == "DOCX" and mode in {"DOCX_STRUCTURED", "TABLE_DOMINANT"}
    if slice_name == "table-heavy":
        return mode == "TABLE_DOMINANT"
    if slice_name == "scanned-pdf":
        return mode == "SCANNED_IMAGE"
    if slice_name == "native-pdf":
        return mode == "NATIVE_TEXT"
    return mode in wanted or purpose in wanted


def skipped_result(fixture: dict[str, Any], reason: str) -> dict[str, Any]:
    return {
        "fixtureCode": fixture.get("fixtureCode"),
        "status": "SKIPPED",
        "smokeOnly": True,
        "projectId": None,
        "documentId": None,
        "parserJobId": None,
        "requirementJobId": None,
        "knowledgeJobId": None,
        "complianceJobId": None,
        "reportJobId": None,
        "pageCount": None,
        "layoutBlockCount": None,
        "tableCount": None,
        "clauseCount": None,
        "automaticRequirementCount": None,
        "manualClauseSeed": 0,
        "manualRequirementSeed": 0,
        "unresolvedSuspiciousEmpty": None,
        "reportIntegrity": "NOT_RUN",
        "tenantIsolation": "NOT_RUN",
        "audit": "NOT_RUN",
        "timings": {},
        "failures": [reason],
        "intakeStatus": fixture.get("status"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default=os.environ.get("SPECAI_API", "http://127.0.0.1:8098"))
    parser.add_argument("--manifest-dir", default=str(ROOT / "evaluation/corpus/manifests"))
    parser.add_argument(
        "--asset-root",
        default=os.environ.get(
            "NANOBASE_CORPUS_ASSET_ROOT",
            str(ROOT / "evaluation/corpus/assets/local"),
        ),
    )
    parser.add_argument("--slice", default="all")
    parser.add_argument("--output-dir", default="/tmp/nanobase-v11-e2e")
    parser.add_argument("--execute-live", action="store_true",
                        help="Execute live API E2E for READY fixtures (default: inventory-only skip)")
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    intake = run_intake(Path(args.manifest_dir), Path(args.asset_root), out_dir)

    results: list[dict[str, Any]] = []
    ready_statuses = {"READY_FOR_SMOKE", "READY_FOR_QUALITY_GATE"}
    for fixture in intake.get("fixtures", []):
        if not matches_slice(fixture.get("slice") or {}, args.slice):
            continue
        if fixture.get("status") not in ready_statuses:
            results.append(skipped_result(
                fixture,
                f"INTAKE_{fixture.get('status')}",
            ))
            continue
        if not args.execute_live:
            results.append(skipped_result(
                fixture,
                "LIVE_EXECUTION_NOT_REQUESTED_USE_--execute-live",
            ))
            continue
        # Live multi-format execution requires dedicated tenant + flags; keep explicit.
        results.append({
            **skipped_result(fixture, "LIVE_MULTI_FORMAT_RUNNER_PENDING_TENANT_FLAGS"),
            "status": "PENDING",
            "api": args.api,
            "assetPath": fixture.get("assetPath"),
        })

    inventory = intake.get("inventory") or {}
    summary = {
        "generatedAt": utc_now(),
        "api": args.api,
        "slice": args.slice,
        "manifestCount": inventory.get("manifestCount", 0),
        "assetCount": inventory.get("assetCount", 0),
        "readyForSmoke": inventory.get("readyForSmoke", 0),
        "readyForQualityGate": inventory.get("readyForQualityGate", 0),
        "results": results,
        "passCount": sum(1 for r in results if r["status"] == "PASS"),
        "failCount": sum(1 for r in results if r["status"] == "FAIL"),
        "skippedCount": sum(1 for r in results if r["status"] == "SKIPPED"),
        "pendingCount": sum(1 for r in results if r["status"] == "PENDING"),
        "decision": (
            "BLOCKED_CORPUS_ASSETS"
            if inventory.get("missingAssets")
            else "READY_FIXTURES_AVAILABLE"
        ),
        "ok": True,
        "note": "PASS is never fabricated for missing assets",
    }
    slice_eval = {
        "generatedAt": utc_now(),
        "slices": {},
        "note": "Accuracy metrics are NOT_SCORED without APPROVED ground truth",
    }
    for name in ["native-pdf", "scanned-pdf", "docx", "table-heavy", "knowledge"]:
        slice_results = [r for r in results if matches_slice(
            next((f.get("slice") for f in intake["fixtures"] if f.get("fixtureCode") == r["fixtureCode"]), {}),
            name,
        )]
        slice_eval["slices"][name] = {
            "fixtureCount": len(slice_results),
            "smokeCount": sum(1 for r in slice_results if r.get("smokeOnly")),
            "qualityGateCount": 0,
            "pass": sum(1 for r in slice_results if r["status"] == "PASS"),
            "fail": sum(1 for r in slice_results if r["status"] == "FAIL"),
            "skipped": sum(1 for r in slice_results if r["status"] == "SKIPPED"),
            "precision": "NOT_SCORED",
            "recall": "NOT_SCORED",
            "criticalRecall": "NOT_SCORED",
            "groundingAccuracy": "NOT_SCORED",
            "numericAccuracy": "NOT_SCORED",
            "manualReviewRate": "NOT_SCORED",
            "timeoutRate": "NOT_SCORED",
            "wallClockP50": "NOT_SCORED",
            "wallClockP95": "NOT_SCORED",
        }

    (out_dir / "run-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    (out_dir / "slice-evaluation.json").write_text(
        json.dumps(slice_eval, indent=2) + "\n", encoding="utf-8"
    )
    # Do not invent empty PASS artifacts for unrun suites.
    for name in ["performance-report.json", "security-report.json", "report-regression.json"]:
        payload = {
            "generatedAt": utc_now(),
            "status": "NOT_RUN",
            "reason": summary["decision"],
        }
        (out_dir / name).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({
        "outputDir": str(out_dir),
        "decision": summary["decision"],
        "skippedCount": summary["skippedCount"],
        "passCount": summary["passCount"],
        "assetCount": summary["assetCount"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
