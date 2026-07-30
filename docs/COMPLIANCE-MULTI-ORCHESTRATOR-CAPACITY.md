# Multi-Orchestrator Capacity

| Field | Value |
|-------|-------|
| Instances | `specai-legal-ai-orchestrator-1`, `specai-legal-ai-orchestrator-b-1` |
| Provider | shared Redis |
| capacityProvider (ready) | `RedisModelCapacityManager` on both |
| Global active model capacity peak | **1** (with maxConcurrency=1) |
| Result | **PASS** |

Overlay: `compose.orchestrator-ha.yaml`.
