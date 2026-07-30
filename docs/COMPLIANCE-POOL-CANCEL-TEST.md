# Pool Cancel Test

| Field | Value |
|-------|-------|
| Job | `f8bb4bf2-7fbc-4716-8f48-3714fbc5f9ce` (PHASE6-POOL-08) |
| HTTP | 200 |
| Cancel latency | **21 ms** |
| Final | CANCELLED |
| Evaluations on cancelled job | 0 (no late COMPLETED) |

Cancel under multi-job pressure did not block on pool acquisition.
