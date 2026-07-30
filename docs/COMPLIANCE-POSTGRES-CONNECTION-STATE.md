# PostgreSQL Connection State (Phase 6)

Sampled via `pg_stat_activity` every ~1.5 s.

| Metric | Peak | Final |
|--------|-----:|------:|
| idle in transaction | **0** | 0 |
| longest open transaction (sec) | **0** | 0 |

Conclusion: model execute does not hold DB transactions; no model-length `idle in transaction`.
