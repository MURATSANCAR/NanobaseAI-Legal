# Known Issues

1. **Hikari pool=5 multi-job gate still PENDING** — Phase 5 did not run ≥8 concurrent long executes under `DATABASE_POOL_SIZE=5`. Blocks `PRODUCTION_READY=true`.
2. Scheduler HA (two reclaim instances) not live-proven in Phase 5.
3. Retry-limit live exhaustion (`WORKER_REPEATEDLY_INTERRUPTED`) not re-run in Phase 5.
4. Redis capacity snapshot may serialize empty lease list as `{}` (Lua empty table); active count remains correct.
5. `MODEL_CAPACITY_PROVIDER=local` remains available for single-process labs only — **not** multi-instance production.

## Resolved in Phase 5

- Process-local-only model capacity for multi-orchestrator (replaced by Redis leases).
- Stale worker could insert evaluation before fencing reject (fixed: fence-before-insert + TX rollback on late race).
- Heartbeat during fault-injection pause prevented reclaim (fixed: suppress heartbeat while paused).
