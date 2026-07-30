# Compliance Final Production Readiness

## Decision

```text
PRODUCTION_READY = true
```

## Mandatory chain (live)

```text
Phase 1–2 TX boundaries PASS
1×5 / timeout / 503 / aggregation PASS
Same-job + same-event PASS
Redis global capacity + multi-orchestrator PASS
Crash/reclaim + stale-worker + cancel/persist PASS
Hikari pool=5 × 8 jobs PASS
```

## Deployment assumptions (required)

```text
Redis capacity provider enabled (not process-local)
MODEL_CAPACITY_FAILURE_POLICY = FAIL_CLOSED
Redis HA in production
Lease/heartbeat/reclaim schedulers active
Fault injection disabled in production
Profile-based global capacity configured to match model runtime
Pool size sized for (worker concurrency + API + schedulers) headroom
```

## Non-blocking residual risks

- Scheduler HA dual-instance live gate PENDING
- Retry-limit exhaustion live gate PENDING
- 12-job stress observation not run (optional)

## Capacity planning note

Under `DATABASE_POOL_SIZE=5`, safe in-flight worker concurrency observed was **3** (plus API/heartbeat headroom). Setting Rabbit listener concurrency ≥ pool size without headroom caused Hikari acquire timeouts during claim/create stampedes in earlier iterations.
