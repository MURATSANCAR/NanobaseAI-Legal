# Phase 1 findings — Document intelligence unblock

## Root causes addressed

| Symptom | Fix |
|---------|-----|
| Docling clauses=0 | Provider chain + page/obligation fallback; layout persist |
| Auto requirements=0 | Chunking + signal-aware empty taxonomy (no silent COMPLETED on suspicious empty) |
| MinIO browser 403 | `MINIO_PUBLIC_ENDPOINT` + streaming proxy downloads |
| 953-byte “success” PDF | Integrity validator before COMPLETED; multi-section renderer |

## Migrations

V29–V32 added; V28 untouched.

## Autonomous gate

`scripts/full_product_e2e_autonomous_dsi.py` — forbids SQL seed; requires clause>0, auto req>0, report ≥1200 bytes with `%PDF`, proxy downloads.

## Residual risks

- Live AI timeouts can still yield PARTIALLY_COMPLETED / knowledge FAILED (now staged).
- Public MinIO must be set in deploy env; proxy is the guaranteed path.
