# Legal Orchestrator Operations

Service name in Compose: `ai-orchestrator`  
Container (EasyMeeting): `specai-legal-ai-orchestrator-1`  
Project name: `specai-legal`

## Required Compose project name

```text
specai-legal
```

## Required Compose files

Always use **both**:

```text
compose.yaml
compose.easymeeting.yaml
```

Never recreate with only `compose.yaml`. That drops the EasyMeeting network/Redis DNS binding and capacity init fails closed.

## Safe recreate command

```bash
docker compose \
  -p specai-legal \
  --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml \
  -f compose.easymeeting.yaml \
  up -d --force-recreate ai-orchestrator
```

Build variant:

```bash
docker compose \
  -p specai-legal \
  --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml \
  -f compose.easymeeting.yaml \
  up -d --build ai-orchestrator
```

## Environment validation

Required (non-secret) checks:

```text
AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT=PT720S
MODEL_CAPACITY_PROVIDER=redis
MODEL_CAPACITY_FAILURE_POLICY=FAIL_CLOSED
REDIS_HOST=actenora-prodlike-redis
```

`AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT` overrides BALANCED `timeoutSeconds` from `AI_MODEL_DEPLOYMENTS_JSON`. FAST profiles are not modified.

Validated runtime note:

```text
Validated runtime value for the current BALANCED local-model profile.
Further performance optimization remains part of v1.1 hardening.
```

BALANCED generation timeout was **600s** when two live DSİ compliance evaluations failed at ~600s with `LLM_GENERATION_TIMEOUT`. After **PT720S** and a correct Compose recreate, DSİ v1.0 regression passed (compliance 17/17, seeds 0, report integrity PASS).

Do **not** treat 720s as a universal optimum.

Inspect without dumping secrets:

```bash
docker inspect specai-legal-ai-orchestrator-1 \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT
```

## Redis DNS validation

```bash
docker exec specai-legal-ai-orchestrator-1 \
  getent hosts actenora-prodlike-redis
```

Expected: a resolved IP for `actenora-prodlike-redis`.

## Readiness validation

Endpoint: `GET /health/ready` (Compose healthcheck uses this path).

Expected fields:

```text
capacityProvider = redis
failurePolicy = FAIL_CLOSED
providerReachable = true
leaseOperationsHealthy = true
balancedGenerationTimeoutSeconds = 720
status = UP
```

From the backend network / host mapping, probe the orchestrator health without installing extra tools in the container when possible (Compose healthcheck already polls readiness).

## Logs

```bash
docker logs specai-legal-ai-orchestrator-1 --since 30m 2>&1 \
  | grep -E 'MODEL_CAPACITY_REDIS_READY|CAPACITY_PROVIDER_INIT_FAILED|generationTimeout|BALANCED_GENERATION_TIMEOUT|FAIL_CLOSED'
```

Healthy signature:

```text
event=MODEL_CAPACITY_REDIS_READY ... failurePolicy=FAIL_CLOSED
generationTimeout=720.0s
```

## Rollback

1. Restore previous `AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT` / deployments JSON from env backup.
2. Recreate with the **same** two Compose files and `-p specai-legal`.
3. Confirm `/health/ready` and Redis DNS before re-running DSİ E2E.

## Known failure signatures

| Signature | Meaning | Action |
|-----------|---------|--------|
| `CAPACITY_PROVIDER_INIT_FAILED` + Redis DNS failure | Wrong Compose project/files; Redis hostname unresolved | Recreate with both Compose files + `-p specai-legal` |
| Capacity FAIL_CLOSED rejecting work | **Expected** when Redis unreachable | Fix network/DNS; do not disable FAIL_CLOSED |
| `LLM_GENERATION_TIMEOUT` ~600s | Generation budget too tight for current BALANCED model path | Confirm `AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT=PT720S` |
| Recreate with only `compose.yaml` | Leaves orchestrator off EasyMeeting Redis network | Never use single-file recreate on EasyMeeting host |

## Redis FAIL_CLOSED root cause (recorded)

```text
Problem:
Legal orchestrator was recreated with the wrong Compose project/file
combination. The container did not join the EasyMeeting network context,
so the Redis service DNS name could not be resolved.

Expected security behavior:
When the Redis capacity provider is unreachable, the system FAIL_CLOSED
and does not accept new model work.

Solution:
Recreate with compose.yaml + compose.easymeeting.yaml and -p specai-legal.

Result:
Redis provider became reachable again and DSİ regression PASSed.
```
