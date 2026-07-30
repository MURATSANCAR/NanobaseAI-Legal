# Known Issues

## Residual (post-production hardening — not go-live blockers)

Priority:

1. Scheduler HA (two reclaim instances) live gate
2. Retry-limit live exhaustion (`WORKER_REPEATEDLY_INTERRUPTED`)
3. Optional 12-job stress observation

## Operational guidance (enforced)

- `databasePoolSize >= workerConcurrency + operationalHeadroom`
- Keep Redis capacity + FAIL_CLOSED; never ship process-local capacity as multi-instance production
- Fault injection must stay off in production
- Startup guardrail: `ComplianceDeploymentGuardrails`

## Closed

- Compliance orchestration Phase 1–6 mandatory gates
- Hikari pool=5 × 8-job pressure
- Global Redis capacity / multi-orchestrator
- Crash/reclaim, stale-worker, cancel/persist, same-event/same-job
