# Production Acceptance Checklist — Document Intelligence Parser

**Status:** Partial pass (2026-08-05)  
**Service:** `document-intelligence`  
**Host evidence:** `/tmp/clause-quality-report.json`, `/tmp/soak-report.json`, `/data/fixtures/`

## Gates

| # | Gate | Evidence | Pass? |
|---|---|---|---|
| 1 | `/health/ready` → `pdf_inspector.library_loaded=true` | live | ✅ |
| 2 | Markdown short-circuit on digital PDF (`shortCircuited=true`) | live | ✅ |
| 3 | Scanned PDF does **not** short-circuit | earlier smoke `pdf_type=scanned` | ✅ |
| 4 | `/metrics` exposes `specai_parser_*` | live | ✅ |
| 5 | Short-circuit ratio visible in Grafana | JSON at `ops/prod-hardening/grafana/parser_dashboard.json` — import pending | ☐ |
| 6 | Error guards reject corrupt / tiny / non-PDF / encrypted / too-many-pages | `GUARD NOT_A_PDF` live + unit tests | ✅ (encrypted live case still optional) |
| 7 | Clause quality eval `passRate >= 0.80` | `/tmp/clause-quality-report.json` passRate=1.0 on 10 slots | ⚠️ bootstrap from DSİ only — replace with 10–15 distinct human goldens |
| 8 | Soak: success ≥ 0.98, digital p95 < 5s, short-circuit ≥ 0.90 | 100p digital, conc=1, jobs=15 → success=1.0, p95=1.53s, sc=1.0 | ✅ (note: conc≥4 saturates uvicorn workers=1) |
| 9 | Rollback: `PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT=false` instant | env | ☐ (ops flip) |
| 10 | No document content in logs | design + spot check | ✅ |

## Findings

- `PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT=120` → documents **>120 pages** drop Markdown and **skip** short-circuit. Soak fixture must be ≤120 pages (use `/data/fixtures/digital-100p.pdf`).
- High concurrency against single uvicorn worker causes submit timeouts; scale workers before 60-way soak.

## Alert suggestions

```promql
(
  sum(rate(specai_parser_path_total{path="pdf_inspector_short_circuit"}[10m]))
  /
  clamp_min(sum(rate(specai_parser_path_total[10m])), 1e-9)
) < 0.70

sum(rate(specai_parser_path_total{outcome="error"}[5m])) > 0.05
sum(rate(specai_parser_guard_total[5m])) > 0.2

histogram_quantile(
  0.95,
  sum by (le) (rate(specai_parser_duration_seconds_bucket{path="pdf_inspector_short_circuit"}[10m]))
) > 2
```

## Sign-off

**Full production unlock** still needs: Grafana import (#5), real multi-tender golden set (#7), optional rollback drill (#9), and worker-scaled soak if 60-way concurrency is a hard requirement.
