# Compliance Final Production Readiness

## Decision

```text
PRODUCTION_READY = true
baseline = compliance-orchestration-v1.0
```

Release manifest: `docs/RELEASE-COMPLIANCE-ORCHESTRATION-V1.0.md`  
Release tag: `compliance-orchestration-v1.0`  
Validated policy hash: `65f7982cf7b27f34433cae2f9a5f8eee`

## Validated profile

```text
PRODUCTION_READY = true

Validated profile:
- Redis distributed model capacity enabled
- Capacity provider FAIL_CLOSED
- Fault injection disabled
- Lease/heartbeat/reclaim enabled
- Database pool includes API and scheduler headroom
- Worker concurrency is bounded below the effective pool capacity
```

Policy:

```text
databasePoolSize >= workerConcurrency + operationalHeadroom
```

Guardrail implementation: `ComplianceDeploymentGuardrails`  
Docs: `docs/COMPLIANCE-DEPLOYMENT-GUARDRAILS.md`

## Mandatory chain (live)

```text
Phase 1–2 TX boundaries PASS
1×5 / timeout / 503 / aggregation PASS
Same-job + same-event PASS
Redis global capacity + multi-orchestrator PASS
Crash/reclaim + stale-worker + cancel/persist PASS
Hikari pool=5 × 8 jobs PASS
Post-test recovery PASS
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
Startup guardrails enforced on production profile
```

## Residual risks (post-production hardening — not go-live blockers)

Priority order:

1. Scheduler HA dual-instance live test
2. Retry-limit exhaustion live test
3. 12-job extended stress observation

## Capacity planning note

Under `DATABASE_POOL_SIZE=5`, safe in-flight worker concurrency observed was **3** with `operationalHeadroom=2`. Raising worker concurrency to consume the whole pool caused Hikari acquire timeouts.
