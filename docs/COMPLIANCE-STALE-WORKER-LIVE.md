# Compliance Stale-Worker Live

## Status

**PENDING live crash fencing; unit fencing covered in Phase 2**

## Expected live sequence

Worker A gen N → lease expire / reclaim → Worker B gen N+1 persists → Worker A late persist → `STALE_WORKER_RESULT` / fencing rejection.

## Available evidence

- Persist path checks `claimed_by` + `lease_generation`.
- Metric: `compliance_stale_worker_result_total` / `compliance_fencing_rejection_total`.
- Cancel-while-RUNNING already proved late model result is not persisted after cancel.
