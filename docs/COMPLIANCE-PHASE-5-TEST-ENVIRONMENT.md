# Compliance Phase 5 — Test Environment

| Item | Value |
|------|-------|
| Host | nanobase `/data/nanobaseai/legal` |
| API | `http://127.0.0.1:8098` |
| Orchestrator A | `127.0.0.1:8095` (`orchestrator-a`) |
| Orchestrator B | `127.0.0.1:8096` (`orchestrator-b`) |
| Capacity provider | Redis (`RedisModelCapacityManager`) |
| Failure policy | `FAIL_CLOSED` |
| Policy hash | `65f7982cf7b27f34433cae2f9a5f8eee` (`reranking=1`, `minimumValidityScore=0.35`) |
| Fault injection (restored) | `COMPLIANCE_FAULT_INJECTION_ENABLED=false` |
| Rabbit concurrency (restored) | `1` |
| Hikari (restored) | `DATABASE_POOL_SIZE=20` |

Compose overlays used: `compose.yaml` + `compose.easymeeting.yaml` + `compose.orchestrator-ha.yaml`.
