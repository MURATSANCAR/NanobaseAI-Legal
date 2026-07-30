# Model Capacity Failure Policy

Production default: **FAIL_CLOSED**.

| Policy | Behavior |
|--------|----------|
| FAIL_CLOSED | Return `CAPACITY_PROVIDER_UNAVAILABLE`; do **not** call model |
| FALLBACK_TO_MODEL_GATEWAY | Reserved |
| SINGLE_INSTANCE_LOCAL_FALLBACK | Reserved / staging only |
| MANUAL_OVERRIDE | Reserved |

When Redis init/ping fails under FAIL_CLOSED, orchestrator uses `FailClosedCapacityManager` stub.

Domain mapping: `CAPACITY_PROVIDER_UNAVAILABLE` (distinct from `MODEL_UNAVAILABLE`).
