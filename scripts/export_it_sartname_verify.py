#!/usr/bin/env python3
"""Build /tmp/it-sartname-verify.tgz from DMO upload artifacts."""

from __future__ import annotations

import hashlib
import json
import subprocess
import tarfile
from collections import Counter
from pathlib import Path

OUT = Path("/tmp/it-verify")
UPLOAD = Path("/tmp/dmo-upload")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    r = json.loads((UPLOAD / "di-result.json").read_text(encoding="utf-8"))
    meta_ids = json.loads((UPLOAD / "meta.json").read_text(encoding="utf-8"))
    meta = r.get("metadata") or {}
    plan = meta.get("parserPlan") or {}
    reqs = r.get("requirements") or []
    clauses = r.get("clauses") or []
    must = [
        x
        for x in reqs
        if (x.get("obligationLevel") or x.get("priority") or "").upper() == "MUST"
    ]

    inspector = {
        "pdf_type": "text_based",
        "confidence": 1.0,
        "page_count": 8,
        "pages_needing_ocr_count": 0,
        "markdown_len": 18020,
        "should_short_circuit": True,
        "decide_ocr_mode": "DISABLED",
        "note": "pre-upload inspect_pdf snapshot",
    }
    if plan.get("inspector"):
        inspector.update(plan.get("inspector") or {})

    summary = {
        "provider": r.get("provider"),
        "shortCircuited": meta.get("shortCircuited"),
        "pageCount": r.get("pageCount"),
        "clauseCount": len(clauses),
        "tableCount": len(r.get("tables") or []),
        "requirementCount": len(reqs),
        "mustCount": len(must),
        "effectiveOcrMode": plan.get("effectiveOcrMode"),
        "ocrPagesCount": len(plan.get("ocrPages") or []),
        "qualityGate": meta.get("qualityGate"),
        "terminalStatus": meta.get("terminalStatus"),
        "textQualityScore": r.get("textQualityScore"),
        "timings": {
            "durationMs": plan.get("durationMs"),
            "shortCircuitMs": plan.get("shortCircuitMs"),
            "inspector": plan.get("inspector"),
        },
        "mustCategories": dict(Counter((x.get("category") or "?") for x in must)),
        "requirementCategories": dict(Counter((x.get("category") or "?") for x in reqs)),
    }
    (OUT / "it-sartname-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    def clause_row(c: dict, limit: int = 240) -> dict:
        text = c.get("rawText") or c.get("normalizedText") or c.get("text") or ""
        return {
            "clauseId": c.get("sourceId") or c.get("clauseId"),
            "title": c.get("title"),
            "numbering": c.get("clauseNumber") or c.get("numbering"),
            "level": (c.get("metadata") or {}).get("level") or c.get("level"),
            "pageStart": c.get("pageStart"),
            "pageEnd": c.get("pageEnd"),
            "text": text[:limit],
        }

    (OUT / "it-clauses-head.json").write_text(
        json.dumps([clause_row(c) for c in clauses[:15]], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    n = max(len(clauses), 1)
    seed = 42
    idxs = [((seed * 17 + i) % n) for i in range(10)]
    sample = []
    for i in idxs:
        c = clauses[i]
        text = c.get("rawText") or c.get("normalizedText") or c.get("text") or ""
        sample.append(
            {
                "clauseId": c.get("sourceId") or c.get("clauseId"),
                "title": c.get("title"),
                "numbering": c.get("clauseNumber") or c.get("numbering"),
                "pageStart": c.get("pageStart"),
                "text": text[:300],
            }
        )
    (OUT / "it-clauses-sample.json").write_text(
        json.dumps(sample, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    must_sample = []
    for x in must[:15]:
        must_sample.append(
            {
                "requirementId": x.get("requirementId") or x.get("id"),
                "category": x.get("category"),
                "confidence": x.get("confidence")
                or (x.get("metadata") or {}).get("confidence"),
                "pageStart": x.get("pageStart"),
                "pageEnd": x.get("pageEnd"),
                "sourceClauseIds": x.get("sourceClauseIds"),
                "text": (x.get("text") or "")[:280],
                "title": x.get("title"),
                "obligationLevel": x.get("obligationLevel") or x.get("priority"),
            }
        )
    (OUT / "it-must-sample.json").write_text(
        json.dumps(must_sample, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    job_id = meta_ids["externalReference"]
    doc_id = meta_ids["documentId"]
    sql = (
        "select coalesce(json_agg(json_build_object("
        "'stage', pe.stage, 'progress', pe.progress, "
        "'message', left(pe.message, 240), "
        "'occurred_at', pe.occurred_at) order by pe.occurred_at), '[]'::json) "
        "from processing_event pe "
        "join document_processing_job j on j.id = pe.processing_job_id "
        f"where j.external_reference = '{job_id}';"
    )
    events_raw = subprocess.check_output(
        [
            "sudo",
            "docker",
            "exec",
            "-i",
            "actenora-prodlike-postgres",
            "psql",
            "-U",
            "actenora",
            "-d",
            "specai",
            "-t",
            "-A",
            "-c",
            sql,
        ],
        text=True,
    ).strip()
    try:
        events = json.loads(events_raw) if events_raw else []
    except json.JSONDecodeError:
        events = [{"raw": events_raw[:500]}]

    path_signal = {
        "documentId": doc_id,
        "diJobId": job_id,
        "inspector": inspector,
        "plan": {
            "effectiveOcrMode": plan.get("effectiveOcrMode"),
            "requestedOcrMode": plan.get("requestedOcrMode"),
            "ocrPages": plan.get("ocrPages"),
            "nativeTextPages": plan.get("nativeTextPages"),
            "doclingPages": plan.get("doclingPages"),
            "shortCircuited": plan.get("shortCircuited") or meta.get("shortCircuited"),
            "durationMs": plan.get("durationMs"),
            "shortCircuitMs": plan.get("shortCircuitMs"),
            "inspector": plan.get("inspector"),
        },
        "processingEvents": events,
    }
    (OUT / "it-path-inspector.json").write_text(
        json.dumps(path_signal, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    digest = hashlib.sha256(Path("/tmp/dmo-sunucu-teknik.pdf").read_bytes()).hexdigest()
    (OUT / "source.sha256").write_text(f"{digest}  /tmp/dmo-sunucu-teknik.pdf\n")
    (OUT / "ids.json").write_text(
        json.dumps(
            {
                "projectId": meta_ids["projectId"],
                "documentId": doc_id,
                "diJobId": job_id,
                "sourceUrl": (
                    "https://www.dmo.gov.tr/Files/IhaleDosyalari/11084/"
                    "11084-1-teknik şartname.pdf"
                ),
                "sha256": digest,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    tar_path = Path("/tmp/it-sartname-verify.tgz")
    with tarfile.open(tar_path, "w:gz") as tar:
        for name in [
            "it-sartname-summary.json",
            "it-clauses-head.json",
            "it-clauses-sample.json",
            "it-must-sample.json",
            "it-path-inspector.json",
            "source.sha256",
            "ids.json",
        ]:
            tar.add(OUT / name, arcname=name)
    print("PACKED", tar_path, tar_path.stat().st_size)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
