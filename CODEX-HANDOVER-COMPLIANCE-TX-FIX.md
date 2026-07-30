# CODEX Handover — Compliance Transaction Boundary Fix

## 1. Doğrulanan kök neden

`ComplianceAnalysisProcessor.process` tek uzun `@Transactional` içinde
`SELECT … FOR UPDATE` ile job satırını kilitliyor, `RUNNING` yazıp commit etmeden
LLM’i (~2 dk) bekliyordu. Polling committed `QUEUED` görüyordu; cancel/worker
lock bekliyordu; `now()` TX başlangıcına sabitleniyordu.

## 2. Önceki transaction sınırı

```text
@Transactional process()
  FOR UPDATE job
  set RUNNING
  for each task: retrieve + LLM + persist
  finalize
→ single commit
```

## 3. Yeni transaction sınırı

```text
process() — no TX
  claimJob REQUIRES_NEW → RUNNING committed
  heartbeat REQUIRES_NEW (20s)
  claimTask REQUIRES_NEW
  evaluate / LLM / slot wait — no TX
  task complete/fail writes (autocommit)
  finalizeJob REQUIRES_NEW
```

## 4. Değiştirilen sınıflar

- `ComplianceAnalysisProcessor`
- `ComplianceJobService` (cancel + get columns)
- `PlatformMetrics` (claim/heartbeat/cancel/task metrics)
- Flyway `V27__compliance_claim_lease_heartbeat.sql`

## 5. Yeni servisler

- `ComplianceJobTransactionService` — short TX claim/heartbeat/cancel/finalize

## 6. Job claim davranışı

Conditional `UPDATE … RETURNING` by `jobId` for `QUEUED` or expired `RUNNING` lease.
Distinct outcomes; not `LLM_UNAVAILABLE`.

## 7. Task claim davranışı

Per-task conditional update to `RUNNING` with lease + worker ownership.

## 8. Lease ve heartbeat

15m lease; 20s heartbeat scheduler owned by process; worker-id guarded updates.

## 9. Cancel davranışı

`requestCancel` sets `cancel_requested_*` without long lock. QUEUED → immediate
CANCELLED. RUNNING → cooperative; late model cannot COMPLETE after cancel flag.

## 10. Slot davranışı

`ProfileSlotManager` capacity unchanged; wait remains outside DB TX.

## 11. Timestamp düzeltmesi

Event times use `clock_timestamp()` / Java `Instant` (not TX-scoped `now()`).

## 12. Error-code ayrıştırması

Claim/lease codes via `orchestrationErrorCode`; model codes stay in
`SemanticEvaluationFailureCode`.

## 13. Yeni migration

`V27__compliance_claim_lease_heartbeat.sql` — claim/lease/heartbeat/cancel columns
+ indexes on job and `requirement_matching_task`.

## 14–19. Orchestrated test results

| Test | Result |
|------|--------|
| 1×1 RUNNING visibility | **PASS** — job `12083325-c54b-45a6-8834-0f4385f236c0`: `QUEUED → RUNNING → COMPLETED`, `claim_duration_ms=3589`, `startedAt < completedAt`, slot acq/rel, `LLM_UNAVAILABLE=0` |
| Polling / lock | **PASS** — GET during RUNNING ~6–15ms |
| Cancel while RUNNING | **PASS** — job `aa28b697-…`, cancelLatencyMs=**15**, `cancel_requested_at` committed under RUNNING, final **CANCELLED** (late model not COMPLETED) |
| 1×5 | PENDING |
| Controlled timeout | PENDING |
| Two workers | PENDING |
| Crash/reclaim | PENDING |

## 20. Çalıştırılan komutlar

```bash
mvn -DskipTests compile
mvn -Dtest=ComplianceTransactionBoundaryArchitectureTest,ComplianceJobTransactionServiceTest test
# nanobase deploy: rsync sources + docker compose up -d --build backend
python3 scripts/orchestrated_compliance_1x1.py
python3 scripts/compliance_cancel_while_running.py
```

## 21. Test sayıları ve sonuçları

- Architecture + claim unit tests: PASS
- Live orchestrated 1×1: PASS (RUNNING visible)
- Live cancel-while-RUNNING: PASS (15ms cancel)
- Broader 1×5 / timeout / dual-worker / reclaim: not yet run

## 22. Bilinen eksikler

- Distinct persisted `WAITING_FOR_SLOT` status not yet separate enum
- Evaluate still uses a short tenant TX that is **suspended** (`NOT_SUPPORTED`) around the model call (connection held, locks may linger on task row until model returns). Job RUNNING is committed before that window; cancel on job row does not block.
- In-flight HTTP abort on cancel optional
- 1×5 / timeout / dual-worker / reclaim live gates pending

## 23. Kalan riskler

- Suspended tenant TX during LLM still holds a pool connection (~model duration); OK at V1 parallelism=1
- Heartbeat REQUIRES_NEW works independently of suspended evaluate TX

## 24. Sonraki öneri

1. Run 1×5 + controlled timeout + dual-worker + reclaim
2. Optionally split evaluate prepare/persist into separate short TXs so task-row locks are not held across LLM
3. Only then revisit multi-candidate / intelligence flags
