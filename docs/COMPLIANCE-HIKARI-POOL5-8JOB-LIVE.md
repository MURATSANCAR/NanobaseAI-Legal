# Hikari Pool=5 × 8-Job Live

| Gate | Sonuç | Kanıt |
|------|-------|-------|
| Pool size doğrulama | **PASS** | `hikaricp_connections_max=5`, min=1 |
| 8 concurrent jobs | **PASS** | 8 job ID; early active=8 |
| Long execute overlap | **PASS** | runningPeak=3, Redis capacity peak=3 |
| Idle in transaction | **PASS** | peak=0 / longestTxSec=0 (n=197) |
| Hikari timeout | **PASS** | timeout peak=0 |
| Poll under pressure | **PASS** | 197 OK, p95=14 ms, max=22 ms |
| Cancel under pressure | **PASS** | job `f8bb4bf2-…`, **21 ms** |
| Persist/finalization | **PASS** | 7 COMPLETED + 1 CANCELLED |
| Redis capacity cleanup | **PASS** | end active=0 |
| Duplicate evaluation | **PASS** | 0 / 0 |
| Post-test recovery | **PASS** | job `dfcb4257-…` COMPLETED |

## Jobs

| Fixture | Job ID | Final |
|---------|--------|-------|
| PHASE6-POOL-01 | `50935cea-f309-4f90-9c04-b40554871674` | COMPLETED |
| PHASE6-POOL-02 | `16583a0c-0a6e-403c-be72-9fa34c01e974` | COMPLETED |
| PHASE6-POOL-03 | `1d782de4-2733-4831-b561-09d6b4a4f4f4` | COMPLETED |
| PHASE6-POOL-04 | `082d1fa2-2a1b-44ea-9939-f3214223ed7f` | COMPLETED |
| PHASE6-POOL-05 | `ec9f93aa-c9c7-4d8e-af2c-fb385d37aca9` | COMPLETED |
| PHASE6-POOL-06 | `04881941-1c25-4dc1-a111-f27b242db560` | COMPLETED |
| PHASE6-POOL-07 | `3a91e822-99e3-4fe9-92e4-110e9c552c5d` | COMPLETED |
| PHASE6-POOL-08 | `f8bb4bf2-7fbc-4716-8f48-3714fbc5f9ce` | CANCELLED |

## Hikari summary (during pool=5)

| Metric | Min | Avg | Peak | Final |
|--------|----:|----:|-----:|------:|
| active | 2 | 2.005 | 3 | 2 |
| idle | 2 | 2.995 | 3 | 3 |
| pending | 0 | 0 | 0 | 0 |
| timeout | 0 | 0 | 0 | 0 |
