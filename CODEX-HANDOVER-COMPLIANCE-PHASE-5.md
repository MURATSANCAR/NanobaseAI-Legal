# CODEX Handover — Compliance Phase 5

## Status label

```text
Phase 1–4: PASS (preserved)
Global Redis capacity: PASS (Option B)
Same-event / crash / stale / cancel-persist / multi-orch capacity: PASS
Hikari pool=5 multi-job: PENDING
PRODUCTION_READY = false
```

## 1. Phase 1–4 özeti

TX boundaries, fencing, timeout/503 taxonomy, same-job claim, aggregation deferred remain PASS. Policy hash restored: `65f7982cf7b27f34433cae2f9a5f8eee`.

## 2. Global slot kök riski

Process-local `ProfileSlotManager` cannot bound multi-orchestrator model concurrency.

## 3. Seçilen capacity provider

**Option B — Redis lease-backed** (`RedisModelCapacityManager`). Not singleton Option A.

## 4–7. Capacity lease / heartbeat / fencing / failure policy

- Lua atomic acquire/release/heartbeat
- TTL crash recovery
- Generation-aware release
- Production `FAIL_CLOSED` → `CAPACITY_PROVIDER_UNAVAILABLE`
- Java port: `ModelCapacityManager` (+ domain types)

## 8. Same-event

PASS — event `6a70b0b8-…` — 1 winner / 1 loser / 1 processed_message row.

## 9. Same-job

PASS (Phase 4) — `8b3c7ef0-…`.

## 10–11. Global + multi-orchestrator capacity

PASS — peak active leases = 1 across `orchestrator-a`/`orchestrator-b`.

## 12–14. Crash/reclaim + generations + capacity TTL

- Task crash reclaim PASS — job `f0a567d1-…`, gen 1→2, evals=1, `docker kill`
- Orchestrator capacity TTL PASS — lease id changed after expire

## 15. Stale-worker

PASS — job `0e86781b-…`, gen 1→2, `PERSIST_REJECTED_STALE`, evals=1.  
Also fixed orphan evaluation: fence-before-insert in `ComplianceTaskPersistenceService`.

## 16–18. Hikari multi-job / PG / poll

**PENDING** — mandatory pool=5 × ≥8 jobs not executed. Restored `DATABASE_POOL_SIZE=20`.

## 19–20. Cancel/persist

- A PASS — `5f60e041-…`, cancel 22 ms, evals=0
- B PASS — `093fd0e8-…`, COMPLETED + cancel 409

## 21. Orchestrator crash lease recovery

PASS — see capacity TTL report.

## 22–23. Scheduler HA / retry limit

PENDING (optional/recommended).

## 24. Duplicate kontrolleri

Stale+crash paths: evaluations=1. Capacity release idempotent in unit/live probes.

## 25. Error-code matrisi

See `docs/COMPLIANCE-PHASE-5-ERROR-CODES.md`.

## 26. Test configuration restore

- FI disabled / token cleared
- Rabbit concurrency=1
- Pool=20
- Policy hash `65f7982cf7b27f34433cae2f9a5f8eee`
- Orchestrator capacity provider remains Redis FAIL_CLOSED (production intent)

## 27. Çalıştırılan komutlar / scripts

- `scripts/phase5_same_event_idempotency_live.py`
- `scripts/phase5_multi_orch_capacity_live.py`
- `scripts/phase5_orchestrator_crash_capacity_live.py`
- `scripts/phase5_stale_cancel_live.py`
- `scripts/phase5_crash_reclaim_live.py`
- compose HA overlay `compose.orchestrator-ha.yaml`

## 28. Başarısız / pending testler

| Gate | Sonuç |
|------|-------|
| Hikari pool=5 multi-job | PENDING |
| Scheduler HA dual instance | PENDING |
| Retry limit live | PENDING |

## 29. Kalan riskler

- Hikari starvation under ≥8 long executes not proven at pool=5
- Dual-backend scheduler SKIP LOCKED HA not live-proven
- Capacity snapshot empty-array JSON encodes as `{}` (cosmetic)

## 30. Production readiness kararı

```text
PRODUCTION_READY = false
```

Reason: mandatory **Hikari pool pressure** gate not closed. Global capacity root risk **is** addressed for multi-orchestrator via Redis; remaining pool gate still blocks true.

Architecture checklist highlights:

- Production capacity manager is **not** process-local when `MODEL_CAPACITY_PROVIDER=redis`
- Capacity provider failure does **not** start unlimited model calls
- Persist fencing validates lease generation before evaluation insert
- Terminal job overwrite by stale worker rejected
