# CODEX Handover — Compliance Phase 4

## Status label

```text
Phase 1: PASS
Phase 2: PASS
1×1 / Cancel: PASS
1×5: PASS
Concurrency / recovery: PARTIAL (improved, still incomplete)
PRODUCTION_READY = false
```

## Gate summary

| Gate | Sonuç | Job/Test ID | Ana kanıt |
|------|-------|-------------|-----------|
| 1×1 regression | PASS | `9b868a1d-…` | Phase 2 |
| Cancel regression | PASS | `4750138c-…` | 18 ms CANCELLED |
| 1×5 | PASS | `d2b2b9ef-…` | reranked=5, dup links=0 |
| Controlled timeout | **PASS** | `15224f20-…` | `MODEL_TIMEOUT` / FAILED |
| Controlled 503 | **PASS** | `f5c8951e-…` | LLM_UNAVAILABLE then COMPLETED |
| Two-worker same event | PENDING | — | not run |
| Two-worker same job | **PASS** | `8b3c7ef0-…` | 1 claim / 1 loser, ~388ms |
| Crash/reclaim | PENDING | — | kill loop not run |
| Stale-worker | PENDING | — | pause+reclaim not run |
| Hikari pool pressure | PENDING | — | multi-job pool=5 not run |
| Cancel/persist race A/B | PENDING | — | barrier not run |
| Aggregation deferred | **PASS** | `a0e2d675-…` | READY_FOR_MODEL → deferred → COMPLETED |
| Terminal-state protection | PASS | Phase 3 `de0cbd38-…` | JOB_ALREADY_COMPLETED |

---

## 1. Phase 1–3 doğrulama özeti

Phase 1–2 TX boundaries PASS. Phase 3 1×5 PASS. Policy after 1×5 restore:

- md5(`configuration_json`) = **`65f7982cf7b27f34433cae2f9a5f8eee`**
- `reranking=1`, `minimumValidityScore=0.35`

## 2. Test ortamı ve configuration hash’leri

See `docs/COMPLIANCE-PHASE-4-TEST-ENVIRONMENT.md`. Fault injection temporarily enabled then restored to `false`.

## 3. Fault injection tasarımı

See `docs/COMPLIANCE-PER-CORRELATION-FAULT-INJECTION.md`.

## 4–6. Timeout / 503 / error-code ayrımı

Timeout job `15224f20-eca9-42e7-94b5-2e2543544b9a` → `MODEL_TIMEOUT`.  
503 job `f5c8951e-…` → first `LLM_UNAVAILABLE`, retry COMPLETED. Codes not collapsed.

## 7–9. Idempotency / concurrent claim / lock

Same-job race PASS (`8b3c7ef0-…`). Loser update 0 rows; claimMs ~388/389; no multi-second lock wait. Same-event dual consumer PENDING.

## 10–15. Crash / reclaim / stale / fencing

**PENDING** live kill + stale persist rejection.

## 16–18. Hikari multi-job / PG / poll

**PENDING** pool=5 multi-job. Phase 3 single-job idleInTx=0 remains.

## 19–20. Cancel/persist barrier

**PENDING**. Cancel-while-RUNNING still PASS.

## 21. Aggregation deferred

PASS — `a0e2d675-…`.

## 22. Terminal-state protection

Prior Phase 3 evidence remains.

## 23. Central slot scope

Orchestrator process-local (see `docs/COMPLIANCE-CENTRAL-SLOT-SCOPE.md`).

## 24–27. Claim/slot/connection/duplicates

Concurrent claim proves conditional UPDATE exclusivity. Full claim-duration micro-breakdown instrumentation not added. No duplicate evaluation on claim race.

## 28. Çalıştırılan komutlar

```bash
# fault injection enable → rebuild backend+orchestrator
# scripts:
sudo python3 scripts/phase4_concurrent_claim_live.py
sudo python3 scripts/phase4_timeout_503_live.py
sudo python3 scripts/phase4_model_timeout_retest.py
sudo python3 scripts/phase4_aggregation_deferred_live.py
# restore FAULT_INJECTION_*=false; recreate containers
```

## 29. Başarısız / çalıştırılmayan

Crash/reclaim, stale-worker, Hikari multi-job, cancel/persist barrier, same-event idempotency.

## 30. Kalan riskler

Multi-worker recovery unproven under SIGKILL; slot capacity not globally shared; pool starvation under N long executes unproven.

## 31. Production readiness

```text
PRODUCTION_READY = false
```

Mandatory incomplete gates: crash/reclaim, stale-worker, Hikari pool pressure, cancel/persist barrier (and same-event dual-worker still pending).

**Kapanış sinyali hâlâ:** aynı job’a eş zamanlı iki claim’de yalnız biri modele gitmeli (**claim race PASS**) **ve** worker kill sonrası yeni generation ile diğer worker işi bitirmeli (**PENDING**).
