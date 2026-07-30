# Compliance Phase 3 Error Codes

| Code | Retryable | Terminal | Task effect | Job effect | Live verified |
|------|-----------|----------|-------------|------------|---------------|
| MODEL_TIMEOUT | yes (policy) | after max | FAILED/RETRY | may partial | **PENDING** |
| MODEL_UNAVAILABLE / LLM_UNAVAILABLE | yes | after max | FAILED | may partial | 0 observed on 1×5 |
| SLOT_WAIT_TIMEOUT | yes | after max | FAILED | may partial | PENDING |
| JOB_ALREADY_CLAIMED | no | n/a | none | none | PENDING concurrent |
| JOB_ALREADY_COMPLETED | no | n/a | none | none | PASS (`de0cbd38`) |
| STALE_WORKER_RESULT | no | n/a | reject persist | none | unit + cancel path |
| WORKER_INTERRUPTED | — | — | — | — | PENDING |
| WORKER_REPEATEDLY_INTERRUPTED | no | yes | FAILED | FAILED | code path only |
| LEASE_EXPIRED | reclaim | — | requeue | requeue | scheduler code |
| CANCEL_REQUESTED / LLM_CANCELLED | no | cancel | CANCELLED | CANCELLED | PASS (18 ms) |
| AGGREGATION_DEFERRED | n/a | no | keep active | stay RUNNING | code only |
| JOB_ALREADY_CANCELLED | no | n/a | none | none | Phase 1/2 |

Do not collapse HTTP timeout, connection reset, 503, incomplete body, or persistence failure into a single `MODEL_TIMEOUT`.
