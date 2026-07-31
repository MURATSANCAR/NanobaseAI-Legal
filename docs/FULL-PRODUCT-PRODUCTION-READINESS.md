# Full Product Production Readiness Decision

**Date:** 2026-07-31  
**Tested commit (local HEAD at lock):** see `nanobase-spec-intelligence-v1.0` tag after lock  
**Compliance baseline (unchanged):** `compliance-orchestration-v1.0` (`e9a44f1`)  
**Semantic policy hash:** `65f7982cf7b27f34433cae2f9a5f8eee`

## Decision

```
FULL_PRODUCT_PRODUCTION_READY=true
baseline=nanobase-spec-intelligence-v1.0
```

Validated scope: controlled production for the tested native-PDF technical specification workflow (DSİ Sulama Otomasyon Genel Teknik Şartnamesi). Broader scanned-PDF, DOCX, and table-heavy GA gates remain pending.

## Live E2E-01 evidence (zero manual seed)

| Field | Value |
|-------|--------|
| DSİ PDF SHA-256 | `82d312e7900954f592537e377993d978e5f219ca9d473cb8271ea793fefb3743` |
| Artifact report | `/tmp/nanobase-e2e/full_product_e2e_autonomous_report.json` (`ok: true`) |
| Full E2E duration | ~2677 s |

| Gate | Sonuç | Live ID / kanıt |
|------|--------|-----------------|
| Project creation | PASS | `aff7e4f5-d668-43ad-98fa-cada1d317bb6` |
| Document upload | PASS | `56c47fc8-7b35-430e-8460-6744b58f13e3` |
| Parser/OCR | PASS | `a2c9b9a2-9ce9-459c-9f65-2a226e97569f` (pageCount=25) |
| Layout blocks | PASS | 24 |
| Clauses | PASS | 24 (TEXT_HIERARCHY; manualClauseSeed=0) |
| Automatic requirements | PASS | job `161d3ce3-06e1-4c7a-a65a-43afd8ece758` / count **17** |
| Manual seed | PASS | clause=0 / requirement=0 |
| Knowledge purpose/stage | PASS | job `a3bfe931-cfd6-4274-abf0-0a3c6d216781` / `SKIPPED_NOT_APPLICABLE` (TENDER_SPEC) |
| Compliance | PASS | `1cfdcdda-b6c6-4fee-8892-94091bab1e01` (17/17 completed) |
| Risk/conflict | PASS | risks=17 / conflicts=0 |
| Human review | PASS | evaluation `83c1de46-f391-42c6-9ef3-60bb264e08de` (GROUNDED, HTTP 200) |
| Report integrity | PASS | job `c4fa2574-049f-4819-8b17-727b0be3dcdd` / artifact `2ef21e7d-e06e-4866-b216-1c45daea237f` / 3191 bytes / `%PDF` |
| Document download | PASS | proxy HTTP 200, SHA match |
| Report download | PASS | proxy HTTP 200, 3191 bytes |
| Tenant isolation smoke | PASS | unauth document/report → 401 |
| Audit chain | PASS | 1243 events visible |

## Deployed image digests (EasyMeeting)

| Image | Digest |
|-------|--------|
| backend | `sha256:30b7acf2db51dede890d0b50d674680d3732e75ea69e250a4037a9a1fa0fb278` |
| frontend | `sha256:b1d0e3e805c67d73a6999acf194e0c41dc1562931df212599303cf7f48510695` |
| document-intelligence | `sha256:775348733abe30e716d771800be5a0e6d773a62ee1bd71416af8b167df8d3427` |
| ai-orchestrator | `sha256:666f6055840c9f28aa6de9d03b300249950eb2b072b287d5f5ff76bc3f370380` |
| opencontracts | `sha256:166d7d86ed2a8577db9ab2193c921e921af6f03ae69fadfece66e19532253969` |

## Migrations

V29–V32 applied (`success=true`) on runtime DB.

## Pending non-blocking gates

- E2E-02..05, E2E-07 live corpus scoring: **PENDING** (harness discovery only; fixtures mostly missing)
- E2E-06 tenant isolation corpus fixture: scaffold READY (asset `.gitkeep` only) — smoke covered in E2E-01
- MinIO presign from container still returns 409 when `MINIO_PUBLIC_ENDPOINT=127.0.0.1`; **proxy download is the validated path**
- Requirement extraction wall-clock remains high (~37 min on this model path)

## Explicit non-goals respected

- Compliance orchestration baseline untouched (fencing / Redis / FAIL_CLOSED / Hikari / policy hash).
- No DSİ-specific static heading rules.
- No SQL/API seed counted as PASS.
