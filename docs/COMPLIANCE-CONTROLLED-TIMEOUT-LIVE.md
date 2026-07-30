# Controlled Timeout Live

| Alan | Değer |
|------|-------|
| Test | Controlled timeout (`DELAY_THEN_TIMEOUT`) |
| Job ID (gateway code) | `a35ca5a0-0a50-47a7-a0e7-368b6d04fbce` → task error `LLM_GENERATION_TIMEOUT`, final `FAILED` |
| Job ID (domain code) | `15224f20-eca9-42e7-94b5-2e2543544b9a` → task error **`MODEL_TIMEOUT`**, final `FAILED` |
| Correlation ID | see reports |
| Retry policy | `LLM_GENERATION_TIMEOUT` not retryable; attempt_count=1 |
| Sonuç | **PASS** |

Active retry policy does not retry generation timeout; terminal `FAILED` with `MODEL_TIMEOUT` is expected.
