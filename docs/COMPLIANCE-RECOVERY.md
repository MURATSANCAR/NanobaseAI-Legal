# Compliance Recovery

## Worker crash

1. Heartbeats stop
2. Lease expires
3. Another consumer reclaim-claims the job (`attempt_count++`)
4. Pending `QUEUED` tasks continue; expired `RUNNING` tasks may be reclaimed

## Duplicate delivery

Second worker receives the same job message → claim fails with
`JOB_ALREADY_CLAIMED` / `LEASE_NOT_EXPIRED` → safe no-op (not `LLM_UNAVAILABLE`).

## Stale worker return

Heartbeat / complete updates require `claimed_by = workerId`. A stale worker cannot
overwrite a reclaimed owner’s results.

## Retry

Prefer task-level retry fields (`next_attempt_at`, `attempt_count`) over flipping the
whole job back to a misleading global `QUEUED` while work is in flight.
