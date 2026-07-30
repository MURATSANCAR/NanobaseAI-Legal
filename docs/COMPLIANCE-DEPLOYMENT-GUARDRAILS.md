# Compliance Deployment Guardrails (v1.0)

## Production invariants

```text
RedisModelCapacityManager enabled (orchestrator)
MODEL_CAPACITY_FAILURE_POLICY = FAIL_CLOSED
Fault injection = false
Heartbeat + reclaim scheduler active
Model concurrency bounded by global Redis capacity
Rabbit worker concurrency must not consume the whole DB pool
API / heartbeat / claim / persist / reclaim / outbox need headroom
```

## Pool capacity policy

```text
databasePoolSize >= workerConcurrency + operationalHeadroom
```

| Symbol | Config |
|--------|--------|
| `databasePoolSize` | `spring.datasource.hikari.maximum-pool-size` / `DATABASE_POOL_SIZE` |
| `workerConcurrency` | `spring.rabbitmq.listener.simple.max-concurrency` / `COMPLIANCE_WORKER_CONCURRENCY` |
| `operationalHeadroom` | `specai.compliance.deployment.operational-headroom` / `COMPLIANCE_DB_OPERATIONAL_HEADROOM` |

`operationalHeadroom` is **deployment-profile configuration**, not a hardcoded Java constant.

Validated live profile (Phase 6):

```text
DATABASE_POOL_SIZE = 5
COMPLIANCE_WORKER_CONCURRENCY = 3
COMPLIANCE_DB_OPERATIONAL_HEADROOM = 2
→ required = 5 → PASS
```

Anti-pattern (failed live):

```text
pool=5, workerConcurrency=8 or 4 with create stampede
→ Hikari timeout / EntityManager errors
```

## Startup enforcement

Class: `ComplianceDeploymentGuardrails`

- Logs `event=COMPLIANCE_DEPLOYMENT_GUARDRAILS` at startup
- Enforced when `specai.compliance.deployment.enforce=true` or `specai.environment=production`
- Production also probes AI orchestrator `/health/ready` when `require-redis-capacity=true` and requires:
  - `capacityProvider=redis`
  - `failurePolicy=FAIL_CLOSED`
  - `providerReachable=true`
  - `leaseOperationsHealthy=true` (isolated `__ORCHESTRATOR_HEALTH__` acquire/release)

## Release gate checklist

```text
[ ] Redis capacity provider confirmed on orchestrator ready probe
[ ] FAIL_CLOSED capacity failure policy
[ ] providerReachable + leaseOperationsHealthy true
[ ] Fault injection disabled
[ ] Lease duration + reclaim interval configured (>0)
[ ] databasePoolSize >= workerConcurrency + operationalHeadroom
[ ] Policy hash / reranking baseline unchanged for semantic quality
[ ] Release tag compliance-orchestration-v1.0 + manifest recorded
```

Release manifest: `docs/RELEASE-COMPLIANCE-ORCHESTRATION-V1.0.md`
