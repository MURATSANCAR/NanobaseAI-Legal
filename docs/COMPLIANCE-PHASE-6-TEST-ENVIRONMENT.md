# Compliance Phase 6 — Test Environment

| Item | Before | During test | Restored |
|------|--------|-------------|----------|
| `DATABASE_POOL_SIZE` | 20 | **5** | 20 |
| Hikari min idle | 20 (default=max) / 1 | **1** | 1 |
| Hikari connectionTimeout | 5000 ms | **10000 ms** | 5000 ms |
| Rabbit concurrency | 1 | **3** | 1 |
| Model `maxConcurrency` | 1 | **3** | 1 |
| Redis capacity | Redis FAIL_CLOSED | same | same |
| Fault injection | false | false | **false** |
| Policy hash | `65f7982cf7b27f34433cae2f9a5f8eee` | unchanged | **`65f7982cf7b27f34433cae2f9a5f8eee`** |

Notes:

- Worker concurrency intentionally capped at **3** under pool=5 to leave connection headroom for polling/heartbeat (first attempts with concurrency=8/4 caused Hikari acquire timeouts during create/claim stampede).
- Eight jobs were still enqueued in the same window and overlapped in `QUEUED`/`RUNNING`; execute peak = 3 (capacity-limited).
- Script: `scripts/phase6_hikari_pool5_8job_live.py`
- Report: `/tmp/phase6_hikari_pool_report.json`
