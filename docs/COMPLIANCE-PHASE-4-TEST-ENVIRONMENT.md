# Compliance Phase 4 — Test Environment Manifest

Captured: 2026-07-30 (nanobase `/data/nanobaseai/legal`)

## Digests / versions

| Component | Value |
|-----------|-------|
| Backend image | `specai-legal-backend:latest` (rebuilt Phase 4) |
| AI orchestrator image | `specai-legal-ai-orchestrator:latest` (rebuilt Phase 4) |
| PostgreSQL | 16.14 |
| Flyway head | V28 `compliance lease generation` (success=t) |
| RabbitMQ / Redis / MinIO | compose stack (prodlike) |
| Worker instances | **1** backend container |
| Orchestrator instances | **1** |
| ProfileSlotManager scope | **AI orchestrator process-local** asyncio.Semaphore |

## Defaults (before Phase 4 temporary overrides)

| Setting | Value |
|---------|-------|
| Hikari maximumPoolSize | `DATABASE_POOL_SIZE` / app default (20 in application.yml, production 30) |
| Lease duration | PT15M |
| Heartbeat interval | 20s (code) |
| Reclaim interval | 30000 ms |
| Max reclaim attempts | 5 |
| Compliance read timeout | PT780S |
| Retry attempts | 1 (after first) |
| Retry backoff | PT2S |
| Evaluation parallelism | 1 |

## Retrieval policy restore proof (Phase 3 leftover)

Policy version `50000000-0000-0000-0000-000000000021`:

| Field | Value |
|-------|-------|
| configuration md5 | `65f7982cf7b27f34433cae2f9a5f8eee` |
| candidateLimits | `{"graph":30,"lexical":10,"metadata":20,"reranking":1}` |
| minimumValidityScore | `0.35` |

This is the restored production/default policy after Phase 3 1×5 temporary `reranking=5` / `minimumValidityScore=0` overrides.

## Phase 4 temporary overrides (must restore)

| Key | before | test | after |
|-----|--------|------|-------|
| COMPLIANCE_FAULT_INJECTION_ENABLED | unset/false | true | restore false |
| COMPLIANCE_FAULT_INJECTION_TOKEN | unset | `phase4-fault-token-7c3a` | remove |
| AI_ORCHESTRATOR_FAULT_INJECTION_* | unset/false | true + same token | restore false/remove |
| COMPLIANCE_LEASE_DURATION | PT15M | short only for crash tests | PT15M |
| DATABASE_POOL_SIZE | default | 5 for Hikari multi-job | restore |
| Orchestrator slot capacity | production | may raise for pool test | restore + hash |

## Fault injection

- Backend: `specai.compliance.fault-injection.enabled` hard-off when `SPECAI_ENVIRONMENT=production`
- Orchestrator: `FAULT_INJECTION_ENABLED` (no production profile path on nanobase development)
- Control: token-gated internal HTTP endpoints only
