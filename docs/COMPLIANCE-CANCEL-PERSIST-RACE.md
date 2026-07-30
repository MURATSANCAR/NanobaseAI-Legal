# Compliance Cancel / Persist Race

## Live evidence (cancel while RUNNING)

| Alan | Değer |
|------|-------|
| Job ID | `4750138c-1700-4dfb-bd6e-b00b954003ba` |
| Cancel latency | **18 ms** |
| Final | CANCELLED |
| Sonuç | **PASS** |

Script: `scripts/compliance_cancel_while_running.py`

Late model response after cancel is not persisted (Phase 2 + this regression).

## Controlled barrier race (persist-before-cancel / cancel-before-persist latch)

**PENDING** — no in-process barrier harness on nanobase. Reverse order (persist then cancel reject) not separately instrumented beyond terminal-state claim skip (`JOB_ALREADY_COMPLETED`).
