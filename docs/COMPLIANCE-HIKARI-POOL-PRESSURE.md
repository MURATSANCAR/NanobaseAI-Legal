# Compliance Hikari Pool Pressure

## Full gate (pool size=5, ≥5 concurrent long jobs)

**PENDING** — production `DATABASE_POOL_SIZE` was not permanently lowered; multi-job pool starvation harness not run.

## Observational live sample (PASS for idle-in-tx / polling)

| Alan | Değer |
|------|-------|
| Test | PG activity during single-job execute |
| Job ID | `5be631e6-124d-4c72-a09c-b62e1231ce52` |
| RUNNING samples | 12 |
| idle in transaction | **0** on all samples |
| GET job latency | 6–11 ms |
| Final | COMPLETED |
| Sonuç | **PASS (sample)** / full pressure **PENDING** |

Script: `scripts/compliance_hikari_sample_during_execute.py`

Phase 2 architecture assertion `hasResource(dataSource)==false` remains necessary but **not sufficient** alone.

## Note

Host `/actuator/prometheus` hikaricp series were not reachable from the published 8098 surface during this run; PostgreSQL `pg_stat_activity` used instead.
