# Compliance Error-Code Matrix

| Hata | Katman | Retry | Job etkisi |
|------|--------|------:|------------|
| JOB_ALREADY_CLAIMED | orchestration | hayır | no-op |
| LEASE_EXPIRED / reclaim | orchestration | evet | reclaim |
| STALE_WORKER_RESULT | orchestration | hayır | reject |
| SLOT_WAIT_TIMEOUT | capacity | evet | retry |
| MODEL_TIMEOUT | model | policy | task fail/timeout |
| MODEL_UNAVAILABLE / LLM_UNAVAILABLE | model | evet | retry/fail |
| MODEL_INVALID_RESPONSE | validation | policy | fail |
| PERSISTENCE_FAILURE | database | evet | retry |
| CANCEL_REQUESTED | control | hayır | cancel |
| AGGREGATION_INCOMPLETE | orchestration | evet | defer finalize |
