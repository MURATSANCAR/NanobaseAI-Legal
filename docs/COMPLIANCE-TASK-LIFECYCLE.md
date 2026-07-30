# Compliance Task Lifecycle

```text
QUEUED → RUNNING → COMPLETED
                → FAILED
                → CANCELLED
                → TIMED_OUT (error_code)
```

Each requirement matching task has independent short commits so polling can observe
progress while sibling tasks continue.

## Claim / lease

`ComplianceJobTransactionService.claimTask` sets `claimed_by`, `claimed_at`,
`heartbeat_at`, `lease_expires_at`, `started_at` using `clock_timestamp()`.

## Completion rule

Model response must not mark the task `COMPLETED` if `cancel_requested_at` is set;
the task becomes `CANCELLED` instead.
