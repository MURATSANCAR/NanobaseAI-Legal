# Production Acceptance Checklist — Document Intelligence Parser

**Status:** In progress  
**Service:** `document-intelligence`  
**Date:** 2026-08-05

## Gates

| # | Gate | Evidence | Pass? |
|---|---|---|---|
| 1 | `/health/ready` → `pdf_inspector.library_loaded=true` | curl / docker exec | ☐ |
| 2 | Markdown short-circuit on digital PDF (`shortCircuited=true`) | parse result metadata | ☐ |
| 3 | Scanned PDF does **not** short-circuit | `pdf_type=scanned` + Docling/OCR path | ☐ |
| 4 | `/metrics` exposes `specai_parser_*` | GET `/metrics` | ☐ |
| 5 | Short-circuit ratio visible in Grafana | dashboard import | ☐ |
| 6 | Error guards reject corrupt / tiny / non-PDF / encrypted / too-many-pages | unit + live negative tests | ☐ |
| 7 | Clause quality eval `passRate >= 0.80` on golden set (10–15 docs) | `clause_quality_eval.py` report | ☐ |
| 8 | Soak: success ≥ 0.98, digital p95 < 5s, short-circuit ≥ 0.90 | `soak_test.py` report | ☐ |
| 9 | Rollback: `PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT=false` instant | env flip | ☐ |
| 10 | No document content in logs | sample log review | ☐ |

## Alert suggestions

```promql
# Short-circuit ratio drop
(
  sum(rate(specai_parser_path_total{path="pdf_inspector_short_circuit"}[10m]))
  /
  clamp_min(sum(rate(specai_parser_path_total[10m])), 1e-9)
) < 0.70

# Error spike
sum(rate(specai_parser_path_total{outcome="error"}[5m])) > 0.05

# Guard flood
sum(rate(specai_parser_guard_total[5m])) > 0.2

# Short-circuit p95 latency
histogram_quantile(
  0.95,
  sum by (le) (rate(specai_parser_duration_seconds_bucket{path="pdf_inspector_short_circuit"}[10m]))
) > 2
```

## Commands

```bash
# Guards + metrics image
docker compose --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml -f compose.easymeeting.yaml \
  build document-intelligence
docker compose --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml -f compose.easymeeting.yaml \
  up -d --no-deps --force-recreate document-intelligence

# Metrics
docker exec specai-legal-document-intelligence-1 \
  python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8090/metrics').read().decode())" | rg specai_parser

# Quality eval
python ops/prod-hardening/evaluation/clause_quality_eval.py \
  --expected /data/fixtures/golden/expected.json \
  --actual /data/fixtures/golden/actual.json \
  --report /tmp/clause-quality-report.json \
  --min-pass-rate 0.80

# Soak
python ops/prod-hardening/load/soak_test.py \
  --endpoint http://127.0.0.1:8090 \
  --digital-pdf /data/fixtures/digital-120p.pdf \
  --concurrency 60 --jobs 120 \
  --report /tmp/soak-report.json
```

## Sign-off

| Role | Name | Date | Signature |
|---|---|---|---|
| Engineering | | | |
| Ops | | | |
| Product | | | |

**Full production unlock requires all gates 1–10 = Pass.**
