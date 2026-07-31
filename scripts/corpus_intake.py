#!/usr/bin/env python3
"""Read-only corpus asset intake for Spec Intelligence v1.1.

Never invents binaries, never moves assets into git, never marks PASS without files.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RELEASE_LICENSE = {
    "APPROVED_INTERNAL_EVALUATION",
    "APPROVED_COMMERCIAL_EVALUATION",
    "PUBLIC_DOMAIN",
    "OPEN_LICENSE",
}
MAGIC = {
    "application/pdf": [b"%PDF"],
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": [b"PK\x03\x04"],
}


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_manifest_schema(manifest: dict[str, Any], schema: dict[str, Any] | None) -> list[str]:
    errors: list[str] = []
    required = schema.get("required", []) if schema else [
        "fixtureCode", "displayName", "slice", "asset", "provenance",
        "license", "privacy", "groundTruth", "expectedBehavior",
    ]
    for key in required:
        if key not in manifest:
            errors.append(f"MISSING_FIELD:{key}")
    license_status = ((manifest.get("license") or {}).get("status"))
    if license_status and license_status not in {
        "PENDING", "APPROVED_INTERNAL_EVALUATION", "APPROVED_COMMERCIAL_EVALUATION",
        "PUBLIC_DOMAIN", "OPEN_LICENSE", "RESTRICTED", "REJECTED",
    }:
        errors.append(f"INVALID_LICENSE_STATUS:{license_status}")
    expected = manifest.get("expectedBehavior") or {}
    if expected.get("manualClauseSeed", 0) != 0:
        errors.append("MANUAL_CLAUSE_SEED_NOT_ZERO")
    if expected.get("manualRequirementSeed", 0) != 0:
        errors.append("MANUAL_REQUIREMENT_SEED_NOT_ZERO")
    if schema:
        try:
            import jsonschema  # type: ignore
            jsonschema.validate(manifest, schema)
        except ImportError:
            pass
        except Exception as exc:  # noqa: BLE001
            errors.append(f"SCHEMA_VALIDATION:{exc}")
    return errors


def verify_magic(path: Path, media_type: str) -> list[str]:
    expected = MAGIC.get(media_type, [])
    if not expected:
        return ["UNSUPPORTED_FORMAT"]
    head = path.read_bytes()[:16]
    if not any(head.startswith(sig) for sig in expected):
        return ["INVALID_FILE_MAGIC"]
    if media_type.endswith("wordprocessingml.document"):
        try:
            with zipfile.ZipFile(path) as zf:
                names = zf.namelist()
                if any(n.startswith("../") or n.startswith("/") for n in names):
                    return ["SECURITY_REJECTED:PATH_TRAVERSAL"]
                if "word/document.xml" not in names and not any(
                    n.endswith("word/document.xml") for n in names
                ):
                    return ["INVALID_FILE:DOCX_MISSING_DOCUMENT"]
                if any(n.lower().endswith(".bin") and "vba" in n.lower() for n in names):
                    return ["SECURITY_REJECTED:MACRO_LIKE_BINARY"]
        except zipfile.BadZipFile:
            return ["INVALID_FILE:BAD_ZIP"]
    return []


def classify(
    *,
    schema_errors: list[str],
    asset_exists: bool,
    file_errors: list[str],
    license_status: str,
    privacy: dict[str, Any],
    ground_truth_status: str,
) -> str:
    if schema_errors:
        return "INVALID_FILE" if any("SCHEMA" in e or "MISSING" in e for e in schema_errors) else "INVALID_FILE"
    if not asset_exists:
        return "MISSING_ASSET"
    if any(e.startswith("SECURITY_REJECTED") for e in file_errors):
        return "SECURITY_REJECTED"
    if any(e.startswith("UNSUPPORTED") for e in file_errors):
        return "UNSUPPORTED_FORMAT"
    if file_errors:
        return "INVALID_FILE"
    if license_status in {"PENDING", "RESTRICTED", "REJECTED"}:
        return "LICENSE_PENDING" if license_status == "PENDING" else "LICENSE_PENDING"
    if privacy.get("redactionStatus") == "NOT_REVIEWED" or privacy.get(
        "containsPersonalData"
    ) == "UNKNOWN" or privacy.get("containsConfidentialData") == "UNKNOWN":
        return "PRIVACY_REVIEW_REQUIRED"
    if ground_truth_status != "APPROVED":
        if license_status in RELEASE_LICENSE:
            return "READY_FOR_SMOKE" if ground_truth_status in {
                "PENDING", "DRAFT", "FIRST_REVIEW", "SECOND_REVIEW"
            } else "GROUND_TRUTH_PENDING"
        return "LICENSE_PENDING"
    if license_status in RELEASE_LICENSE:
        return "READY_FOR_QUALITY_GATE"
    return "LICENSE_PENDING"


def resolve_asset(asset_root: Path, relative: str) -> Path:
    return (asset_root / relative).resolve()


def main() -> int:
    parser = argparse.ArgumentParser(description="Corpus intake (read-only)")
    parser.add_argument("--manifest-dir", default=str(ROOT / "evaluation/corpus/manifests"))
    parser.add_argument(
        "--asset-root",
        default=os.environ.get(
            "NANOBASE_CORPUS_ASSET_ROOT",
            str(ROOT / "evaluation/corpus/assets/local"),
        ),
    )
    parser.add_argument("--schema", default=str(ROOT / "evaluation/corpus/schemas/corpus-manifest.schema.json"))
    parser.add_argument("--policy", default=str(ROOT / "evaluation/corpus/policy/quality-gates-v1.1.json"))
    parser.add_argument("--output", default="/tmp/nanobase-corpus/intake-report.json")
    parser.add_argument("--inventory-output", default="/tmp/nanobase-corpus/corpus-inventory.json")
    parser.add_argument("--write-proposed-manifest-patches", action="store_true")
    parser.add_argument(
        "--patch-dir",
        default="/tmp/nanobase-corpus/proposed-manifest-patches",
    )
    args = parser.parse_args()

    manifest_dir = Path(args.manifest_dir)
    asset_root = Path(args.asset_root)
    schema = load_json(Path(args.schema)) if Path(args.schema).is_file() else None
    policy = load_json(Path(args.policy)) if Path(args.policy).is_file() else {}

    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.inventory_output).parent.mkdir(parents=True, exist_ok=True)
    if args.write_proposed_manifest_patches:
        Path(args.patch_dir).mkdir(parents=True, exist_ok=True)

    fixtures: list[dict[str, Any]] = []
    hashes: dict[str, list[str]] = {}
    for path in sorted(manifest_dir.glob("*.json")):
        manifest = load_json(path)
        schema_errors = validate_manifest_schema(manifest, schema)
        relative = ((manifest.get("asset") or {}).get("relativePath")) or ""
        asset_path = resolve_asset(asset_root, relative) if relative else None
        exists = bool(asset_path and asset_path.is_file())
        file_errors: list[str] = []
        digest = None
        size = None
        if exists and asset_path is not None:
            size = asset_path.stat().st_size
            digest = sha256_file(asset_path)
            hashes.setdefault(digest, []).append(manifest.get("fixtureCode", path.stem))
            file_errors.extend(
                verify_magic(asset_path, (manifest.get("asset") or {}).get("mediaType", ""))
            )
            declared = (manifest.get("asset") or {}).get("sha256")
            if declared and declared.lower() != digest.lower():
                file_errors.append("SHA256_MISMATCH")
        status = classify(
            schema_errors=schema_errors,
            asset_exists=exists,
            file_errors=file_errors,
            license_status=((manifest.get("license") or {}).get("status") or "PENDING"),
            privacy=manifest.get("privacy") or {},
            ground_truth_status=((manifest.get("groundTruth") or {}).get("status") or "PENDING"),
        )
        proposed = None
        if exists and digest and not (manifest.get("asset") or {}).get("sha256"):
            proposed = {
                "fixtureCode": manifest.get("fixtureCode"),
                "proposedAssetSha256": digest,
                "note": "SHA computed read-only; not auto-committed",
            }
            if args.write_proposed_manifest_patches:
                patch_path = Path(args.patch_dir) / f"{manifest.get('fixtureCode')}.sha256.patch.json"
                patch_path.write_text(json.dumps(proposed, indent=2) + "\n", encoding="utf-8")
        fixtures.append({
            "fixtureCode": manifest.get("fixtureCode"),
            "manifestPath": str(path),
            "status": status,
            "assetPath": str(asset_path) if asset_path else None,
            "assetExists": exists,
            "sha256": digest,
            "sizeBytes": size,
            "mediaType": (manifest.get("asset") or {}).get("mediaType"),
            "licenseStatus": (manifest.get("license") or {}).get("status"),
            "privacy": manifest.get("privacy"),
            "groundTruthStatus": (manifest.get("groundTruth") or {}).get("status"),
            "slice": manifest.get("slice"),
            "schemaErrors": schema_errors,
            "fileErrors": file_errors,
            "proposedManifestUpdate": proposed,
            "releaseEligibleLicense": (
                (manifest.get("license") or {}).get("status") in RELEASE_LICENSE
            ),
        })

    duplicates = [
        {"sha256": digest, "fixtureCodes": codes}
        for digest, codes in hashes.items()
        if len(codes) > 1
    ]
    for item in fixtures:
        if item["sha256"] and any(d["sha256"] == item["sha256"] for d in duplicates):
            if item["status"] not in {"MISSING_ASSET", "INVALID_FILE", "SECURITY_REJECTED"}:
                item["status"] = "DUPLICATE_ASSET"

    counts = Counter(item["status"] for item in fixtures)
    inventory = {
        "generatedAt": utc_now(),
        "manifestCount": len(fixtures),
        "assetCount": sum(1 for item in fixtures if item["assetExists"]),
        "readyForSmoke": counts.get("READY_FOR_SMOKE", 0),
        "readyForQualityGate": counts.get("READY_FOR_QUALITY_GATE", 0),
        "missingAssets": [i["fixtureCode"] for i in fixtures if i["status"] == "MISSING_ASSET"],
        "licensePending": [i["fixtureCode"] for i in fixtures if i["status"] == "LICENSE_PENDING"],
        "privacyReviewRequired": [
            i["fixtureCode"] for i in fixtures if i["status"] == "PRIVACY_REVIEW_REQUIRED"
        ],
        "groundTruthPending": [
            i["fixtureCode"] for i in fixtures
            if i["status"] in {"GROUND_TRUTH_PENDING", "READY_FOR_SMOKE"}
            and i.get("groundTruthStatus") != "APPROVED"
        ],
        "duplicates": duplicates,
        "invalidFiles": [
            i["fixtureCode"] for i in fixtures
            if i["status"] in {"INVALID_FILE", "UNSUPPORTED_FORMAT", "SECURITY_REJECTED"}
        ],
        "statusCounts": dict(counts),
        "policyVersion": policy.get("version"),
        "minimumMatrix": policy.get("minimumMatrix"),
        "assetRoot": str(asset_root),
    }
    report = {
        "generatedAt": utc_now(),
        "ok": True,
        "decision": "BLOCKED_CORPUS_ASSETS" if inventory["missingAssets"] else "INTAKE_COMPLETE",
        "fixtures": fixtures,
        "inventory": inventory,
    }
    Path(args.output).write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Path(args.inventory_output).write_text(
        json.dumps(inventory, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(json.dumps({
        "output": args.output,
        "inventory": args.inventory_output,
        "manifestCount": inventory["manifestCount"],
        "assetCount": inventory["assetCount"],
        "readyForSmoke": inventory["readyForSmoke"],
        "readyForQualityGate": inventory["readyForQualityGate"],
        "decision": report["decision"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
