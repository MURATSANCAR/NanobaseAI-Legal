# Release Manifest — compliance-orchestration-v1.0

## Identity

| Field | Value |
|-------|-------|
| Release tag | `compliance-orchestration-v1.0` |
| Readiness | `PRODUCTION_READY` |
| Validated semantic policy hash | `65f7982cf7b27f34433cae2f9a5f8eee` |
| Guardrail version | `compliance-deployment-guardrails-v1` |
| Baseline lock date | 2026-07-30 |

## Artifact digests (deploy host snapshot)

Captured from live deploy host images at baseline lock:

| Artifact | Image ID / digest |
|----------|-------------------|
| Backend | `sha256:f54bf3a45a09cbbd1f0574c72e6c3e9455caf7354e41bf4d0c61c4ce7b9e7604` |
| AI orchestrator | `sha256:9f92e4eea8e9a278a6bcb340a2f60d4b50361b327e4f35b3174299c5a58b7165` |

> Re-build after this tag will produce new digests; record the digest of the **tagged release build** in the deploy runbook when promoting.

## Database

| Field | Value |
|-------|-------|
| Latest Flyway migration | `V28__compliance_lease_generation.sql` |

## Capacity / concurrency (production invariants)

| Field | Value |
|-------|-------|
| Redis capacity provider | `redis` (`MODEL_CAPACITY_PROVIDER`) |
| Capacity failure policy | `FAIL_CLOSED` |
| Model capacity lease TTL | `120000` ms |
| Capacity heartbeat interval | `30` s |
| Worker concurrency (default compose) | `COMPLIANCE_WORKER_CONCURRENCY` (default `1`; Phase-6 validated under pool=5 → `3`) |
| Database pool size | `DATABASE_POOL_SIZE` (default `20`; Phase-6 stress validated at `5` with workers=`3`) |
| Operational headroom | `COMPLIANCE_DB_OPERATIONAL_HEADROOM` (default `2`) |
| Pool policy | `databasePoolSize >= workerConcurrency + operationalHeadroom` |

## Lease / reclaim

| Field | Value |
|-------|-------|
| Task lease duration | `COMPLIANCE_LEASE_DURATION` = `PT15M` |
| Reclaim interval | `COMPLIANCE_RECLAIM_INTERVAL_MS` = `30000` |
| Reclaim batch size | `COMPLIANCE_RECLAIM_BATCH_SIZE` = `10` |

## Fault injection

| Field | Value |
|-------|-------|
| Backend FI | `false` (required in production) |
| Orchestrator FI | `false` (required in production) |

## Readiness probe contract (`GET /health/ready`)

Must report (not Redis PING alone):

```text
capacityProvider = redis
failurePolicy = FAIL_CLOSED
providerReachable = true
leaseOperationsHealthy = true
```

Lease health uses isolated profile `__ORCHESTRATOR_HEALTH__` (acquire/release); does **not** consume product model capacity slots.

HTTP `503` when not ready under Redis provider mode.

## Startup enforcement

`ComplianceDeploymentGuardrails` on production profile:

- Pool ≥ workers + headroom
- FI disabled
- Lease/reclaim configured
- Orchestrator ready probe fields above when `require-redis-capacity=true`

## Residual hardening (non-blocking)

1. Scheduler HA dual-instance live
2. Retry-limit exhaustion live
3. 12-job extended stress

## Baseline lock rule

Subsequent work must land as **versioned changes on top of this baseline**. Do not silently rewrite validated transaction boundaries, fencing, Redis capacity, or deployment guardrail behavior in place.
