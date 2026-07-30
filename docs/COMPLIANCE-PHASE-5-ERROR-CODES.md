# Compliance Phase 5 Error Codes

| Code | Layer | Retryable | Task | Job | Notes |
|------|-------|-----------|------|-----|-------|
| IDEMPOTENT_DUPLICATE | messaging | n/a | none | none | duplicate event claim |
| JOB_ALREADY_CLAIMED | claim | no | none | skip | same-job race loser |
| MODEL_TIMEOUT | model | no | FAILED/retry policy | may fail | mapped from LLM_* timeout |
| MODEL_UNAVAILABLE | model | yes* | retry | — | not capacity |
| LEASE_EXPIRED | lease | reclaim | requeue | reclaim | |
| WORKER_INTERRUPTED | worker | reclaim | requeue | reclaim | |
| STALE_WORKER_RESULT | persist fencing | no | reject | keep winner | |
| CAPACITY_FULL | capacity | wait/retry | wait | — | not LLM_UNAVAILABLE |
| CAPACITY_WAIT_TIMEOUT | capacity | limited | fail/retry | — | |
| CAPACITY_LEASE_LOST | capacity | reclaim | no persist | reclaim | |
| CAPACITY_PROVIDER_UNAVAILABLE | capacity | no unlimited call | fail | — | FAIL_CLOSED |
| CANCEL_REQUESTED | cancel | no | CANCELLED | CANCELLED | |
| AGGREGATION_DEFERRED | finalize | n/a | — | stay running | |
| WORKER_REPEATEDLY_INTERRUPTED | reclaim | no | FAILED | FAILED | max attempts |

\* Model unavailable retries remain subject to global deadline / cancellationCompleted rules.
