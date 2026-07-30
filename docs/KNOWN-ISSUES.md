# Known Issues — Compliance Phase 3

## Resolved / improved this phase

- Dead evaluate helpers removed from `ComplianceAnalysisProcessor` (prepare/execute/persist-only).
- `finalizeJob` returns `AGGREGATION_DEFERRED` instead of throwing when active tasks remain.
- `ComplianceLeaseReclaimScheduler` added (lease expiry → QUEUED + outbox republish).
- Live **1×5** PASS (`d2b2b9ef-…`, reranked=5).
- Cancel regression PASS (18 ms).
- Observational execute-phase idle-in-transaction = 0.

## Remaining (PRODUCTION_READY = false)

1. Controlled model timeout live gate.
2. Concurrent two-worker / duplicate delivery while RUNNING (`JOB_ALREADY_CLAIMED`).
3. Crash + reclaim live (`docker kill` + generation bump + terminal).
4. Stale-worker live fencing after reclaim.
5. Hikari `maximumPoolSize=5` multi-job pressure + prometheus series.
6. Controlled cancel/persist barrier race.
7. Aggregation deferred live with stuck `READY_FOR_MODEL`.
8. `ProfileSlotManager` remains **instance-local** — not a global multi-worker capacity guarantee.
9. Retrieval corpus for Tier III requirement needed seeded fixtures for 1×5; production docs must not assume rich candidates without fixtures.
