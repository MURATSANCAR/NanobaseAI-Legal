# Production Acceptance Checklist — Document Intelligence Parser

**Status:** FULL PROD GATES PASS (2026-08-05)  
**Service:** `document-intelligence`  
**Evidence pack:** `ops/prod-hardening/fullprod-unlock/`

## Gates

| # | Gate | Evidence | Pass? |
|---|---|---|---|
| 1 | `/health/ready` → `pdf_inspector.library_loaded=true` | live | ✅ |
| 2 | Markdown short-circuit on digital PDF (`shortCircuited=true`) | live + rollback-on | ✅ |
| 3 | Scanned PDF does **not** short-circuit | `scanned-sample` → `BOUNDED_DOCLING` | ✅ |
| 4 | `/metrics` exposes `specai_parser_*` | live | ✅ |
| 5 | Short-circuit ratio visible in Grafana | dashboard `nanobase-parser-routing` + Prometheus job `specai-document-intelligence` | ✅ |
| 6 | Error guards + state machine (`PDF_ENCRYPTED` → MANUAL_REVIEW) | core functions + tests | ✅ |
| 7 | Clause quality eval `passRate >= 0.80` on **8 distinct** docs | `/tmp/clause-quality-report.json` passRate=1.0 | ✅ |
| 8 | Soak: success ≥ 0.98, digital p95 < 5s, short-circuit ≥ 0.90 | `/tmp/soak-report.json` | ✅ |
| 9 | Rollback drill: flag false → Docling/native, true → SC | `ROLLBACK_DRILL_RESULT.md` **PASS** | ✅ |
| 10 | No document content in logs | design + spot check | ✅ |
| 11 | Durable image (core A–D baked) | rebuild `specai-legal-document-intelligence:latest` | ✅ |
| 12 | Auto gate script | `fullprod_gate_check.py` **9/9 PASS** | ✅ |

## Findings

- `PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT=120` → documents **>120 pages** drop Markdown and **skip** short-circuit.
- High concurrency against single uvicorn worker causes submit timeouts; scale workers before 60-way soak (optional).
- Golden set now uses 8 distinct document IDs (HBYS, DSİ 100p/120p/original, synthetic, MDA, scanned, mini idari) — not DSİ 10-slot clones.

## Sign-off

**Full production unlock (automatic + executed manual gates): PASS.**  
Pilot açmak için teknik engel yok. Worker scale yalnızca yüksek concurrency soak istenirse gerekir.
