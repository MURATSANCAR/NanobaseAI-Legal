# CURSOR handover — Nanobase Spec Intelligence v1.1

## 1. Foundation commit

- Tip / wiring: `cf19d1d` (already on `main`)
- Corpus intake chain: `6f17f4d` → `3fc620a` → `c56a8f6` (synced with `origin/main`)
- `nanobase-spec-intelligence-v1.1` tag **not** created

## 2. v1.0 baseline verification

- `compliance-orchestration-v1.0` → `e9a44f1`
- `nanobase-spec-intelligence-v1.0` → `10a1cad`
- V28–V32 unchanged

## 3. V33 deployment

- **PASS** — Flyway version `33` (`spec intelligence v11 foundations`) applied on prod-like DB
- Backend image: `sha256:c679ba46a388dfb56b5a827e50a59895bd826ad6315fe3953d1de61243df5dcb`
- Deployed commit content from workspace HEAD `c56a8f6` (deploy dir is not a git checkout)

## 4–8. Corpus

- Manifests: 15
- Binaries: 0 → `BLOCKED_CORPUS_ASSETS`
- License / privacy / GT: pending
- Asset root used: `/data/nanobaseai/legal/evaluation/corpus/assets/local`
- Request pack: `docs/V1.1-CORPUS-ASSET-REQUEST.md`

## 9. DSİ v1.0 regression

Started after V33 + integration skip=0 gates. Fresh parser job `fa1f112c-…` READY; requirement job `0423ea52-…` long-running (status may remain QUEUED until mega-transaction commits). Final PASS/FAIL recorded in `/tmp/nanobase-v11-recovery/dsi-regression.json` when complete — do not invent IDs.

## 10–15. Multi-format E2Es

Not run — corpus binaries missing.

## 16. Delivery strategy

Profile B proxy-only still valid; DIRECT_PUBLIC PENDING.

## 17–18. Performance / security

NOT_RUN (no fabricated PASS).

## 19. Tenant isolation

v1.0 preserved; per-format retest pending corpus.

## 20. Slice quality

NOT_SCORED.

## 21–22. Fixes this recovery

- Host SSH recovered
- V33 applied
- `CREATE EXTENSION vector` on prod-like `specai`
- Added `tests/integration/**` with fail-closed `INTEGRATION_REQUIRE_*=1`
- CI job `integration-redis-pgvector` in `.github/workflows/production-gates.yml`

## 23. Remaining blockers

1. Licensed corpus binaries (15)
2. Privacy + APPROVED ground truth
3. Finish/confirm DSİ regression artifact
4. Live E2E-02–07

## 24. BROAD_DOCUMENT_GA_READY

```text
BROAD_DOCUMENT_GA_READY = false
DECISION_REASON = BLOCKED_CORPUS_ASSETS
```
