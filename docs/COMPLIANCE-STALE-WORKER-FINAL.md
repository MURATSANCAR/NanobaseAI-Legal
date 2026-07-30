# Stale-Worker Final

| Field | Value |
|-------|-------|
| Job ID | `0e86781b-a6ab-4d87-b846-5fa20b4fa924` |
| Worker A → B | gen 1 → gen 2 |
| Pause | `PAUSE_BEFORE_PERSIST` + heartbeat suppress |
| Reclaim | yes |
| Worker A persist | `COMPLIANCE_TASK_PERSIST_REJECTED_STALE` |
| Evaluations | **1** |
| Result | **PASS** |

Fix shipped: fence **before** evaluation insert in `ComplianceTaskPersistenceService` (prevents orphan evaluation on stale persist).
