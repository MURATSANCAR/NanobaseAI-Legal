# Known Issues — Compliance Phase 2

## Resolved in Phase 2

- Suspended tenant TX / held DB connection across model call replaced by
  prepare (`REQUIRES_NEW`) → execute (`NEVER`) → persist (`REQUIRES_NEW`).
- `lease_generation` fencing (V28) rejects stale worker persists.

## Remaining

1. Live gates still open: 1×5, controlled timeout, two-worker, crash/reclaim,
   connection-pool pressure harness.
2. Distinct `WAITING_FOR_SLOT` status optional; prepare uses `READY_FOR_MODEL`.
3. Processor may still contain unused legacy helper methods (cleanup debt).
4. Intelligence feature flags remain dual-gated and off.
