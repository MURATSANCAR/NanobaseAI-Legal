# Compliance Fencing Tokens

Columns: `compliance_analysis_job.lease_generation`, `requirement_matching_task.lease_generation`.

On claim: `lease_generation = lease_generation + 1` (RETURNING value stored in `PreparedComplianceTask`).

Persist / heartbeat require:

```text
claimed_by = workerId AND lease_generation = prepared.leaseGeneration
```

Mismatch → `STALE_WORKER_RESULT` / fencing rejection; result discarded.
