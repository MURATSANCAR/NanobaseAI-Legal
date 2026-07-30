# Compliance Job Lifecycle

```text
QUEUED → RUNNING → COMPLETED
                 → PARTIALLY_COMPLETED
                 → FAILED
                 → CANCELLED
```

Cooperative cancel while running:

```text
status = RUNNING
cancel_requested_at != null
```

Workers check cancel after claim, before each task, after slot acquire, after model,
and before retry. Cancel does **not** require holding the job row lock across LLM.

## Claim

Atomic conditional update by `jobId` (not table scan):

- Claim when `QUEUED`, or `RUNNING` with expired lease
- Outcomes: `CLAIMED`, `ALREADY_COMPLETED`, `ALREADY_CANCELLED`,
  `CLAIMED_BY_OTHER_WORKER`, `LEASE_NOT_EXPIRED`, `NOT_FOUND`
- Claim failures are **not** mapped to `LLM_UNAVAILABLE`

## Finalization

Idempotent aggregation from DB task statuses (not in-memory counters only).
Does not finalize while non-cancel active tasks remain.
