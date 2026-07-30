# Cancel / Persist Final

## Test A — Cancel before persist

| Field | Value |
|-------|-------|
| Job ID | `5f60e041-5820-4988-8a88-564f971a09ae` |
| Cancel latency | **22 ms** |
| Job/Task | CANCELLED |
| Evaluations | 0 |
| Result | **PASS** |

## Test B — Persist before cancel

| Field | Value |
|-------|-------|
| Job ID | `093fd0e8-05c6-4dac-a442-11d86c184bbb` |
| Status | COMPLETED → cancel HTTP **409** no-op |
| Evaluations | 1 |
| Result | **PASS** |
