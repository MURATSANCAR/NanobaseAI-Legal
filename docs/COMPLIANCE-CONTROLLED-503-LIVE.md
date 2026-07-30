# Controlled 503 Live

| Alan | Değer |
|------|-------|
| Test | `RETURN_503` once then success |
| Job ID | `f5c8951e-8b01-4d8a-adf7-8b4be9f6c95d` |
| First attempt log | `failureCode=LLM_UNAVAILABLE` |
| Final | `COMPLETED` (retry succeeded) |
| Distinction | Timeout → `MODEL_TIMEOUT`; 503 → `LLM_UNAVAILABLE`/`MODEL_UNAVAILABLE` then retry |
| Sonuç | **PASS** |
