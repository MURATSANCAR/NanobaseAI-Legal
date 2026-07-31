# CURSOR handover — Nanobase Spec Intelligence v1.1

## 1. Foundation commit

Foundation already on `main` (not rewritten; remote-synced):

- Tip including wiring: `cf19d1d`
- Initial V33 foundations commit: `6864c6c`
- Message history contains interim noisy commits; content is additive and flag-default-OFF.
- Requested release tag `nanobase-spec-intelligence-v1.1` **not** created.

## 2. v1.0 baseline verification

- `compliance-orchestration-v1.0` → `e9a44f1` intact
- `nanobase-spec-intelligence-v1.0` → `10a1cad` intact
- V28–V32 migrations not modified by v1.1 work

## 3. V33 deployment

Last observed Flyway on prod-like DB: **V32** (V33 not applied yet).
Backend redeploy + DSİ regression blocked in this session by **nanobase host SSH unreachable** (`38.247.162.28` timeout).
Do not claim V33 deployed until Flyway shows version 33.

## 4. Corpus manifest inventory

15 manifests in `evaluation/corpus/manifests/` covering native/scanned/DOCX/table/knowledge.

## 5. Corpus binary inventory

**0** binaries in default asset root (`evaluation/corpus/assets/local`, gitignored).

Optional root: `NANOBASE_CORPUS_ASSET_ROOT`.

## 6. License status

All manifests: `PENDING`.

## 7. Privacy status

All manifests: personal/confidential `UNKNOWN`, redaction `NOT_REVIEWED`.

## 8. Ground-truth status

Template DRAFT only; **0 APPROVED**.

## 9. DSİ v1.0 regression

Re-run after V33 deploy with flags OFF. Expected: ok=true, manual seeds 0, report integrity PASS.

## 10–15. Live multi-format E2Es

Scanned / DOCX / table / certificate / datasheet / report regression: **NOT RUN** — `BLOCKED_CORPUS_ASSETS`.

## 16. Delivery strategy

Profile B (`BACKEND_PROXY_ONLY`) remains valid; `DIRECT_PUBLIC` PENDING without public DNS/TLS.

## 17–18. Performance / security

NOT_RUN this phase (no empty PASS artifacts claimed).

## 19. Tenant isolation

v1.0 evidence preserved; per-format retest pending corpus.

## 20. Slice quality metrics

All accuracy fields `NOT_SCORED` (no APPROVED ground truth).

## 21–22. Failed attempts / fixes

OCR lookalike false positive on clean `IP65` fixed earlier. Corpus PASS not fabricated.

## 23. Remaining blockers

1. Licensed binaries for 15-fixture matrix
2. Privacy review
3. APPROVED ground truth (≥10)
4. V33 deploy + DSİ regression confirmation
5. Flag-scoped live E2E-02–07

## 24. BROAD_DOCUMENT_GA_READY decision

```text
BROAD_DOCUMENT_GA_READY = false
DECISION_REASON = BLOCKED_CORPUS_ASSETS
```
