# Live Error Codes (Phase 4)

| Code | Live source | Job/evidence |
|------|-------------|--------------|
| MODEL_TIMEOUT | Controlled timeout | `15224f20-…` task `MODEL_TIMEOUT` |
| LLM_UNAVAILABLE / MODEL_UNAVAILABLE | Controlled 503 then retry | `f5c8951e-…` log then COMPLETED |
| JOB_ALREADY_CLAIMED (conditional) | Concurrent claim loser | claim race PASS (0 rows for loser) |
| AGGREGATION_DEFERRED | Stuck READY_FOR_MODEL | `a0e2d675-…` |
| CANCEL_REQUESTED | Cancel regression | Phase 3 |
| LEASE_EXPIRED / STALE_WORKER_RESULT / WORKER_INTERRUPTED | Crash/stale | **PENDING** |
