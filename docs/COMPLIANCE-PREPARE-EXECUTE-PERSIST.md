# Compliance Prepare / Execute / Persist

## Boundaries

| Phase | TX | Connection |
|-------|----|------------|
| Prepare | `REQUIRES_NEW` | Held only during prepare |
| Execute | `Propagation.NEVER` | **None** |
| Persist | `REQUIRES_NEW` | Held only during persist |

After prepare commits, the Hikari connection returns to the pool before model HTTP starts.

## Beans

- `ComplianceTaskPreparationService` — retrieval, rerank, snapshots, `READY_FOR_MODEL`
- `ComplianceTaskModelExecutionService` — LLM only; fails if a TX is active
- `ComplianceTaskPersistenceService` — fencing-checked persist

## DTO

`PreparedComplianceTask` is immutable and carries no JPA entities.
