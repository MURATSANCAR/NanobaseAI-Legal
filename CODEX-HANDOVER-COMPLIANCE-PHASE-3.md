# CODEX Handover — Compliance Phase 3

## Status label

```text
Phase 1 transaction-lock fix: PASS
Phase 2 prepare/execute/persist refactor: PASS
1×1 regression: PASS (Phase 2 job 9b868a1d-…; claim 789 ms)
Cancel regression: PASS (Phase 3 job 4750138c-…; 18 ms)
1×5 live: PASS (d2b2b9ef-…)
Multi-candidate/concurrency/recovery gates: PARTIAL / PENDING
Compliance orchestration production-ready: HAYIR — henüz değil
PRODUCTION_READY = false
```

## Gate summary

| Gate | Sonuç | Ana kanıt |
|------|-------|-----------|
| 1×1 regression | PASS | Phase 2 `9b868a1d-…` |
| Cancel regression | PASS | `4750138c-…` cancelLatencyMs=18 → CANCELLED |
| 1×5 | PASS | `d2b2b9ef-…` candidates=6 reranked=5 selected=5 COMPLETED |
| Controlled timeout | PENDING | No per-correlation fault injection |
| Two workers | PARTIAL | Terminal duplicate skip PASS; concurrent RUNNING skip PENDING |
| Crash/reclaim | PENDING | Scheduler deployed; kill cycle not run |
| Stale-worker | PENDING | Unit fencing only |
| Hikari pool pressure | PARTIAL | idleInTx=0 sample PASS; pool=5 multi-job PENDING |
| Cancel/persist race | PARTIAL | Cancel-while-RUNNING PASS; barrier harness PENDING |
| Aggregation deferred | PENDING | Code returns AGGREGATION_DEFERRED; live stuck-task PENDING |

---

## 1. Phase 1–2 doğrulama özeti

Phase 1: short claim TX; RUNNING poll-visible; cancel unlocked.  
Phase 2: Prepare REQUIRES_NEW → Execute NEVER → Persist REQUIRES_NEW + fencing.  
`hasResource(dataSource)==false` during execute (architecture proof, not pool proof).

## 2. Dead-code incelemesi

Removed unused retrieval/confidence/semantic constructor deps and legacy helpers from `ComplianceAnalysisProcessor`. Path is claim → prepare → execute → persist → finalize only. Reference search: no remaining `NOT_SUPPORTED` evaluate path.

## 3. Test ortamı

nanobase `/data/nanobaseai/legal` — PostgreSQL, RabbitMQ, Redis, MinIO, AI orchestrator, backend worker. API `http://127.0.0.1:8098`.

## 4. Fixture’lar

`COMPLIANCE_1X5_TIER_TEST` — five seeded `evidence_fragment` rows + temporary `reranking=5`, `minimumValidityScore=0`. Restored after run. Policy ID `50000000-0000-0000-0000-000000000021` restored to reranking=1 / validity=0.35.

## 5–6. 1×1 / Cancel regressions

1×1: Phase 2 PASS.  
Cancel: Phase 3 PASS — job `4750138c-1700-4dfb-bd6e-b00b954003ba`, 18 ms.

## 7–8. 1×5 + duplicates

Job `d2b2b9ef-0e05-445d-9f93-624d548a3b45`  
Task `027a8677-fb31-4e5b-8fd1-fec1ac49a9e1`  
QUEUED→RUNNING→COMPLETED; claim_duration_ms=3719; job_duration_ms=81213; duplicate evidence links=0.

## 9–11. Timeout / retry / slot

Controlled timeout **PENDING**. Slot acq/rel observed on 1×5 (orchestrator logs).

## 12–13. Two-worker / duplicate delivery

Job `de0cbd38-…`: claim_ok=1, evaluation=1, second claim after COMPLETED → `JOB_ALREADY_COMPLETED` (2 ms). Concurrent RUNNING `JOB_ALREADY_CLAIMED` **PENDING**.

## 14–16. Crash/reclaim / scheduler / lease generation

`ComplianceLeaseReclaimScheduler` deployed. Live kill/reclaim **PENDING**. Lease generation increments on claim (observed gen=1 on fresh jobs).

## 17. Stale-worker

Live **PENDING**.

## 18–20. Hikari / PG / polling

Sample job `5be631e6-…`: 12× RUNNING samples, idleInTx=0, GET latency 6–11 ms. Full pool=5 pressure **PENDING**.

## 21. Cancel/persist race

Cancel-while-RUNNING PASS. Controlled barrier **PENDING**.

## 22. Aggregation deferred

Code path added; live **PENDING**.

## 23. Terminal-state koruması

Duplicate after COMPLETED → `JOB_ALREADY_COMPLETED` (job `de0cbd38-…`).

## 24. RabbitMQ ack/recovery

ACK + lease reclaim model documented in `docs/COMPLIANCE-RABBITMQ-ACK-RECOVERY.md`.

## 25. Error-code matrisi

See `docs/COMPLIANCE-PHASE-3-ERROR-CODES.md`.

## 26. Çalıştırılan komutlar

```bash
# deploy
rsync … ComplianceAnalysisProcessor.java ComplianceJobTransactionService.java ComplianceLeaseReclaimScheduler.java
docker compose … up -d --build backend

# live gates
sudo python3 scripts/orchestrated_compliance_1x5.py
sudo python3 scripts/compliance_cancel_while_running.py
sudo python3 scripts/compliance_dual_worker_live.py
sudo python3 scripts/compliance_hikari_sample_during_execute.py

# local tests
mvn -Dtest=ComplianceTransactionBoundaryArchitectureTest,ComplianceJobTransactionServiceTest test
```

## 27. Test sayıları

Architecture + claim unit: PASS. Live: 1×5 PASS, cancel PASS, hikari sample PASS, dual-worker PARTIAL.

## 28. Başarısız / çalıştırılmamış

Controlled timeout, crash/reclaim kill, stale-worker live, Hikari pool=5 multi-job, aggregation deferred live, concurrent dual-worker RUNNING claim.

## 29. Kalan riskler

- Instance-local ProfileSlotManager.
- Reclaim scheduler unproven under SIGKILL.
- Outbox relay latency can delay duplicate-delivery observations.
- Full backend suite not re-run end-to-end on nanobase this session (targeted architecture/unit only).

## 30. Production kararı

```text
PRODUCTION_READY = false
```

Reason: at least one primary gate pending/fail among  
1×5 (PASS), controlled timeout (PENDING), two workers (PARTIAL), crash/reclaim (PENDING), stale-worker (PENDING), Hikari pool pressure (PARTIAL).

Next critical signals still:

1. Concurrent claim skip while RUNNING + reclaim after kill.  
2. Hikari maxPool=5 with ≥5 long executes without starving poll/heartbeat/cancel.
