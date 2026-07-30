# Central Slot Scope

| Component | Scope |
|-----------|-------|
| AI orchestrator capacity (production default) | **Redis global leases** (`RedisModelCapacityManager`) |
| Process-local `ProfileSlotManager` | Fallback only when `MODEL_CAPACITY_PROVIDER=local` — **not** multi-instance safe |
| Java `ModelCapacityManager` port | Provider-neutral API for future backend-side consumers |

Multi-orchestrator live proof: `docs/COMPLIANCE-MULTI-ORCHESTRATOR-CAPACITY.md`.
