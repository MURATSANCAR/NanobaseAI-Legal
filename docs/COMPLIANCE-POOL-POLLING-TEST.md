# Pool Polling Test

While 8 jobs overlapped under pool=5:

| Metric | Value |
|--------|------:|
| GET `/compliance-analyses/{id}` count | 197 |
| HTTP errors | 0 |
| min / avg / p95 / max latency (ms) | 6 / 9.4 / 14 / 22 |

No connection timeout, lock timeout, or 5xx on poll path.
