# Compliance Claim, Lease, Heartbeat

## Lease

Default lease: **15 minutes** (`ComplianceJobTransactionService.DEFAULT_LEASE`).

## Heartbeat

While a job is processing, a daemon scheduler (~20s) calls:

```text
heartbeat(jobId, activeTaskId, workerId, newLeaseExpiry)
```

in a short `REQUIRES_NEW` transaction. Updates only rows owned by `claimed_by = workerId`.

Failures increment `compliance_heartbeat_failure_total` and are logged; they do not
abort the main evaluation loop.

## Reclaim

Another worker may claim a `RUNNING` job/task only when `lease_expires_at < clock_timestamp()`.
`attempt_count` increments on each successful claim.
