# Global Capacity Live

| Field | Value |
|-------|-------|
| Test | Different jobs / same profile — global capacity=1 across orchestrators |
| Orchestrator A/B | `orchestrator-a` / `orchestrator-b` |
| Race | 1× ACQUIRED + 1× CAPACITY_FULL |
| Peak active | 1 |
| After release | second ACQUIRED |
| Result | **PASS** |

Script: `scripts/phase5_multi_orch_capacity_live.py`.

This is **not** same-job claim and **not** same-event idempotency.
