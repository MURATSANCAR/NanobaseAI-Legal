# Compliance Connection Lifecycle

Model execution must not hold a suspended tenant transaction.

```text
prepare TX begin → apply tenant → load snapshots → READY_FOR_MODEL → commit
→ connection returned to pool
→ execute (NEVER) model HTTP
→ persist TX begin → fencing check → write → commit
```

Assertions:

- `TransactionSynchronizationManager.isActualTransactionActive() == false` during execute
- `compliance_connection_held_during_model_total` target: **0**
- During model, `pg_stat_activity` should not show `idle in transaction` for the worker’s prepare connection
