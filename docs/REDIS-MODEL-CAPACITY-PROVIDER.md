# Redis Model Capacity Provider

Implementation: `services/ai-orchestrator/capacity.py` (`RedisModelCapacityManager`).

## Env

| Variable | Default | Notes |
|----------|---------|-------|
| `MODEL_CAPACITY_PROVIDER` | `redis` | `local` disables global capacity (not multi-instance safe) |
| `MODEL_CAPACITY_FAILURE_POLICY` | `FAIL_CLOSED` | Required for production |
| `MODEL_CAPACITY_LEASE_TTL_MS` | `120000` | Crash recovery window |
| `MODEL_CAPACITY_HEARTBEAT_INTERVAL_SECONDS` | `30` | Must be `< TTL` |
| `REDIS_HOST` / `REDIS_PASSWORD` | compose-provided | EasyMeeting: `actenora-prodlike-redis` |

## API probe

`GET /v1/capacity/{profile}/snapshot` — active lease count across orchestrators.

## Live proof

Multi-orchestrator race (`scripts/phase5_multi_orch_capacity_live.py`): **PASS** — 1 ACQUIRED / 1 CAPACITY_FULL; peak active=1.
