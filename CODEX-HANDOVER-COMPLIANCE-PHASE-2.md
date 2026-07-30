# CODEX Handover — Compliance Phase 2 (Prepare/Execute/Persist)

## 1. Önceki düzeltmenin doğrulaması

Phase 1: `QUEUED→RUNNING→COMPLETED` poll-visible; cancel ~15ms; V27 lease/cancel.

## 2. Askıda transaction/connection bulgusu

Phase 1 used `PROPAGATION_NOT_SUPPORTED` inside a tenant TX → suspended connection held for model duration.

## 3. Yeni prepare/execute/persist sınırları

Prepare `REQUIRES_NEW` → commit → Execute `NEVER` → Persist `REQUIRES_NEW`.

## 4. Değiştirilen sınıflar

`ComplianceAnalysisProcessor`, `ComplianceJobTransactionService`, `PlatformMetrics`.

## 5. Prepared DTO

`PreparedComplianceTask` (+ snapshots).

## 6. Model transaction guard

`ComplianceTaskModelExecutionService` `@Transactional(NEVER)` + active-TX assertions.

## 7. Fencing token yapısı

V28 `lease_generation` on job/task; persist/heartbeat require match.

## 8. Heartbeat davranışı

Generation-aware; outcomes UPDATED/LEASE_LOST/TASK_TERMINAL/JOB_CANCELLED.

## 9–11. Claim / aggregation / Rabbit

Claim remains short conditional UPDATE; finalize aggregates DB task statuses including `READY_FOR_MODEL`; consumer ack unchanged (idempotency service).

## 12–20. Live results

| Test | Result |
|------|--------|
| 1×1 regression | **PASS** — job `9b868a1d-…`: RUNNING visible, COMPLETED; prepare→execute(~127s)→persist; claim_duration_ms=789; slot acq/rel; `LLM_UNAVAILABLE=0` |
| Cancel regression | **PASS** — cancelLatencyMs=**24**, final CANCELLED |
| 1×5 | PENDING |
| Timeout | PENDING |
| Two-worker | PENDING |
| Crash/reclaim | PENDING |
| Stale-worker | Unit fencing reject covered; live PENDING |
| Connection-pool | PENDING (execute asserts no DataSource resource holder) |
| Cancel/persist race | Covered by cancel-while-RUNNING late model reject |

## 21–23. Slot / connection / errors

Slot capacity unchanged. Execute path asserts no pooled connection held (`hasResource(dataSource)`). Error matrix documented.

## 24. Migration

`V28__compliance_lease_generation.sql` — applied on nanobase (`success=t`)

## 25–26. Tests

Architecture + claim + fencing unit tests. Live 1×1 + cancel PASS. Remaining multi-candidate/crash/pool gates pending.

## 27. Kalan riskler

Dead helper methods may remain on processor. Full 1×5 / dual-worker / pool harness not yet run live.

## 28. Sonraki öneri

1×5 (rerank=5) → controlled timeout → two-worker → reclaim/stale live → Hikari pool pressure.