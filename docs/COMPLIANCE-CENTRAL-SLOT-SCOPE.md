# Central Slot Scope

| Layer | Scope |
|-------|-------|
| Backend | No model slot |
| AI orchestrator `ProfileSlotManager` | **Process-local** asyncio.Semaphore |
| Redis distributed | No |
| DB lease for model slots | No |
| Model gateway | Depends on llama-server / deployment |

Single orchestrator instance ⇒ central for that deployment. Multiple orchestrator replicas would each apply local capacity ⇒ total concurrency can exceed intended profile limit.
