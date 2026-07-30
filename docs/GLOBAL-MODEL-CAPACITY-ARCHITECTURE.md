# Global Model Capacity Architecture

## Root risk

Process-local `ProfileSlotManager` (asyncio semaphore) only limits concurrency **inside one orchestrator process**. Two replicas with `maxConcurrency=1` can still send **2** concurrent model requests.

## Decision

**Option B — distributed/global slot management** via Redis lease-backed capacity.

Option A (singleton orchestrator) is **not** selected for multi-instance production readiness.

## Port

Java provider-neutral port:

- `com.nanobase.specai.capacity.application.ModelCapacityManager`
- Domain records/enums under `com.nanobase.specai.capacity.domain`

Runtime enforcement today lives in the AI orchestrator (`services/ai-orchestrator/capacity.py`) because model calls are issued there.

## Lease model

- Key: `specai:model-capacity:{PROFILE}:leases`
- Acquire is atomic Lua: purge expired → count active → insert lease if under capacity
- Heartbeat refreshes `expiresAtMs` for same `leaseId` + `generation`
- Release is idempotent and generation-fenced
- Crash without `finally` release recovers via TTL expiry

## Results

| Outcome | Meaning |
|---------|---------|
| ACQUIRED | Lease granted |
| CAPACITY_FULL / WAIT_TIMEOUT | Capacity denied |
| PROVIDER_UNAVAILABLE | Fail-closed; no unlimited model call |
| CAPACITY_LEASE_LOST | Heartbeat lost mid-call |

These are **not** collapsed into `LLM_UNAVAILABLE`.
