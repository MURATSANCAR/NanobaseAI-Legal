#!/usr/bin/env python3
"""Corpus E2E harness scaffold (E2E-02..07).

Runs fixture manifests under testdata/corpus/fixtures/*.json when present.
Until fixtures are checked in, exits 0 with SKIPPED status for missing assets.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "testdata" / "corpus" / "fixtures"
POLICY = ROOT / "testdata" / "corpus" / "policy" / "gates-v1.json"
OUT = Path(os.environ.get("CORPUS_REPORT", "/tmp/corpus_e2e_report.json"))


def main() -> int:
    policy = json.loads(POLICY.read_text(encoding="utf-8")) if POLICY.is_file() else {}
    results = []
    if not FIXTURES.is_dir():
        FIXTURES.mkdir(parents=True, exist_ok=True)
    manifests = sorted(FIXTURES.glob("*.json"))
    if not manifests:
        results.append({
            "id": "HARNESS",
            "status": "SKIPPED",
            "reason": "No fixture manifests yet — add testdata/corpus/fixtures/*.json",
        })
    for path in manifests:
        manifest = json.loads(path.read_text(encoding="utf-8"))
        asset = ROOT / manifest.get("asset", "")
        results.append({
            "id": manifest.get("id", path.stem),
            "status": "READY" if asset.is_file() else "SKIPPED",
            "asset": str(asset),
            "gate": manifest.get("gate"),
        })
    report = {"policy": policy, "results": results, "ok": all(
        r["status"] in {"READY", "SKIPPED", "PASS"} for r in results
    )}
    OUT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
