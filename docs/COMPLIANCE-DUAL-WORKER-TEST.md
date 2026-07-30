# Compliance Dual-Worker Test

## Attempted live run

| Alan | Değer |
|------|-------|
| Test | Duplicate outbox delivery same jobId |
| Job ID | `de0cbd38-239b-4233-98e5-e6ff10f830e8` |
| Worker A claim | generation=1, attempt=1 |
| Duplicate event | `7c8e076c-1ab2-4800-9891-38abf820a791` |
| Evaluation count | 1 |
| Final status | COMPLETED |
| Second claim outcome | `ALREADY_COMPLETED` / `JOB_ALREADY_COMPLETED` (claimDurationMs=2) |
| Concurrent RUNNING skip | **Not observed** (outbox relayed after terminal) |
| Sonuç | **PARTIAL / PENDING** for concurrent two-worker |

## Evidence that did pass

- Single successful claim while RUNNING.
- Single evaluation row.
- Lease generation did not increase from duplicate delivery.
- Terminal duplicate delivery safely no-op’d (`JOB_ALREADY_COMPLETED`).

## Gap

Outbox relay published the duplicate after the job completed (~34s). Concurrent `JOB_ALREADY_CLAIMED` while RUNNING was not captured. Two JVM worker instances were not started.

## ProfileSlotManager scope

`services/ai-orchestrator/app.py` `ProfileSlotManager` is **process-local asyncio.Semaphore** (instance-local). Not Redis/DB distributed. Multi-instance capacity is not globally enforced.
