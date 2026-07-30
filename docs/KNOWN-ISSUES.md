# Known Issues — Compliance Phase 4

## Closed this phase

- Per-correlation fault injection (orchestrator + backend pauses)
- Concurrent same-job claim race PASS
- Controlled timeout → `MODEL_TIMEOUT` PASS
- Controlled 503 → unavailable then retry PASS
- Live `AGGREGATION_DEFERRED` PASS
- Phase 3 policy restore hash documented: `65f7982cf7b27f34433cae2f9a5f8eee`

## Still open (PRODUCTION_READY = false)

1. Crash/reclaim with SIGKILL
2. Live stale-worker fencing after reclaim
3. Hikari pool=5 multi-job pressure
4. Cancel/persist barrier races A/B
5. Same-event idempotency dual-consumer
6. ProfileSlotManager still instance-local
