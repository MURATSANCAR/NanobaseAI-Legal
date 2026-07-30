# Known Issues

## Residual (non-blocking)

1. Scheduler HA (two reclaim instances) live gate not run.
2. Retry-limit live exhaustion (`WORKER_REPEATEDLY_INTERRUPTED`) not re-run in Phase 6.
3. Optional 12-job stress observation not run.

## Operational guidance

- Keep worker concurrency strictly below Hikari `maximumPoolSize` (leave ≥1–2 connections for API/heartbeat/schedulers).
- Do not stampede 8 parallel `POST compliance-analyses` creates against pool=5; enqueue jobs quickly but avoid create-TX pileup.

## Closed in Phase 5–6

- Process-local-only model capacity (Redis leases).
- Stale-worker orphan evaluation insert.
- Hikari pool=5 × 8-job pressure gate.
- Crash/reclaim, cancel/persist barriers, same-event idempotency.
