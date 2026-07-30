# Full Product Production Readiness Decision

**Date:** 2026-07-31  
**Baseline locked:** `compliance-orchestration-v1.0` (`e9a44f1`) — do not regress Prepare/Execute/Persist lease fencing.

## Decision

```
FULL_PRODUCT_PRODUCTION_READY=false
```

Live autonomous DSİ job IDs are required for `true`. Until E2E-01 is executed against the EasyMeeting stack with this build and produces PASS with recorded IDs, readiness remains **false**.

## Phase delivery status

| Phase | Scope | Status |
|-------|--------|--------|
| 1A | ClauseSegmentationProvider chain + V29 layout/recurring persist + Docling page fallback | Implemented |
| 1B | Clause chunking + empty outcome taxonomy + V30 counters | Implemented |
| 1C | `MINIO_PUBLIC_ENDPOINT` + document/report proxy download + access audit | Implemented |
| 1D | ReportIntegrityValidator before COMPLETED + multi-section PDF | Implemented |
| 1E | Autonomous DSİ script (no SQL seed) | Script ready — live run pending |
| 2 | Knowledge stages / purpose isolation / reuse | Implemented in processor |
| 3 | UI review actions, architecture tests, corpus harness | Partial — gates not all live-green |

## How to flip to true

Run:

```bash
SPECAI_API=http://127.0.0.1:8098 \
E2E_PDF=/path/to/DSI_Sulama_Otomasyon_Genel_Teknik_Sartname.pdf \
python3 scripts/full_product_e2e_autonomous_dsi.py
```

Require report JSON with `ok: true` and populate below:

| Gate | Live ID |
|------|---------|
| projectId | _pending_ |
| documentId | _pending_ |
| requirementJobId | _pending_ |
| complianceJobId | _pending_ |
| reportJobId | _pending_ |

## Known blockers until live PASS

1. Live E2E-01 not yet executed on this build (autonomous script only).
2. Knowledge may still FAIL under AI orchestrator saturation — stage taxonomy now records why.
3. Corpus E2E-02..07 fixtures are scaffolded; precision/recall gates not yet scored on production models.
4. Browser download still depends on correct `MINIO_PUBLIC_ENDPOINT` in `/etc/nanobaseai/legal.env` (proxy path is the fail-safe).

## Explicit non-goals respected

- Compliance orchestration baseline untouched.
- No DSİ-specific heading/verb hardcoding as product success.
- Manual SQL seed E2E is not counted as PASS.
