"""Global model capacity leases backed by Redis (Lua-atomic).

FAIL_CLOSED when Redis is required but unavailable.
Lease TTL recovers capacity after worker/orchestrator crash without finally-release.
"""
from __future__ import annotations

import json
import logging
import os
import time
import uuid
from dataclasses import dataclass
from typing import Any

LOG = logging.getLogger("specai.ai_orchestrator.capacity")

ACQUIRE_LUA = """
-- KEYS[1] = leases hash key
-- ARGV: nowMs, maxConcurrency, leaseId, ownerJson, leaseTtlMs
local key = KEYS[1]
local now = tonumber(ARGV[1])
local maxc = tonumber(ARGV[2])
local leaseId = ARGV[3]
local owner = ARGV[4]
local ttl = tonumber(ARGV[5])

local all = redis.call('HGETALL', key)
local active = 0
for i = 1, #all, 2 do
  local payload = cjson.decode(all[i + 1])
  if payload.expiresAtMs ~= nil and payload.expiresAtMs <= now then
    redis.call('HDEL', key, all[i])
  else
    active = active + 1
  end
end

if active >= maxc then
  return cjson.encode({status='CAPACITY_FULL', active=active, max=maxc})
end

local expires = now + ttl
local record = cjson.decode(owner)
record.expiresAtMs = expires
record.generation = 1
redis.call('HSET', key, leaseId, cjson.encode(record))
redis.call('PEXPIRE', key, math.max(ttl * 4, 60000))
return cjson.encode({
  status='ACQUIRED',
  leaseId=leaseId,
  generation=1,
  expiresAtMs=expires,
  active=active + 1,
  max=maxc
})
"""

HEARTBEAT_LUA = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local leaseId = ARGV[2]
local generation = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local raw = redis.call('HGET', key, leaseId)
if not raw then
  return cjson.encode({status='NOT_FOUND'})
end
local payload = cjson.decode(raw)
if payload.expiresAtMs ~= nil and payload.expiresAtMs <= now then
  redis.call('HDEL', key, leaseId)
  return cjson.encode({status='LEASE_EXPIRED'})
end
if tonumber(payload.generation or 0) ~= generation then
  return cjson.encode({status='LEASE_LOST'})
end
payload.expiresAtMs = now + ttl
payload.generation = generation
redis.call('HSET', key, leaseId, cjson.encode(payload))
redis.call('PEXPIRE', key, math.max(ttl * 4, 60000))
return cjson.encode({status='UPDATED', expiresAtMs=payload.expiresAtMs, generation=generation})
"""

RELEASE_LUA = """
local key = KEYS[1]
local leaseId = ARGV[1]
local generation = tonumber(ARGV[2])
local raw = redis.call('HGET', key, leaseId)
if not raw then
  return cjson.encode({status='ALREADY_RELEASED'})
end
local payload = cjson.decode(raw)
if tonumber(payload.generation or 0) ~= generation then
  return cjson.encode({status='STALE_LEASE'})
end
redis.call('HDEL', key, leaseId)
return cjson.encode({status='RELEASED'})
"""

SNAPSHOT_LUA = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local all = redis.call('HGETALL', key)
local active = 0
local leases = {}
for i = 1, #all, 2 do
  local payload = cjson.decode(all[i + 1])
  if payload.expiresAtMs ~= nil and payload.expiresAtMs <= now then
    redis.call('HDEL', key, all[i])
  else
    active = active + 1
    leases[#leases + 1] = {leaseId=all[i], expiresAtMs=payload.expiresAtMs, ownerId=payload.ownerId}
  end
end
return cjson.encode({active=active, leases=leases})
"""


@dataclass
class CapacityLease:
    lease_id: str
    model_profile: str
    owner_id: str
    generation: int
    acquired_at_ms: int
    expires_at_ms: int


class RedisModelCapacityManager:
    def __init__(
        self,
        redis_url: str,
        *,
        key_prefix: str = "specai:model-capacity",
        default_lease_ttl_ms: int = 120_000,
        failure_policy: str = "FAIL_CLOSED",
    ) -> None:
        import redis  # type: ignore

        self._redis = redis.Redis.from_url(redis_url, decode_responses=True)
        self._key_prefix = key_prefix
        self._default_lease_ttl_ms = default_lease_ttl_ms
        self._failure_policy = failure_policy.upper()
        self._acquire_script = self._redis.register_script(ACQUIRE_LUA)
        self._heartbeat_script = self._redis.register_script(HEARTBEAT_LUA)
        self._release_script = self._redis.register_script(RELEASE_LUA)
        self._snapshot_script = self._redis.register_script(SNAPSHOT_LUA)
        # connectivity probe
        self._redis.ping()
        LOG.info(
            "event=MODEL_CAPACITY_REDIS_READY prefix=%s failurePolicy=%s",
            key_prefix,
            self._failure_policy,
        )

    @property
    def failure_policy(self) -> str:
        return self._failure_policy

    @property
    def provider_name(self) -> str:
        return "redis"

    def _key(self, profile: str) -> str:
        return f"{self._key_prefix}:{profile.strip().upper()}:leases"

    def acquire(
        self,
        *,
        model_profile: str,
        max_concurrency: int,
        owner_id: str,
        correlation_id: str | None,
        job_id: str | None = None,
        task_id: str | None = None,
        lease_ttl_ms: int | None = None,
        wait_timeout_seconds: float = 0.0,
        poll_interval_seconds: float = 0.25,
    ) -> tuple[str, CapacityLease | None, float]:
        """Returns (status, lease|None, wait_ms)."""
        started = time.monotonic()
        deadline = started + max(0.0, wait_timeout_seconds)
        ttl = lease_ttl_ms or self._default_lease_ttl_ms
        lease_id = str(uuid.uuid4())
        owner = {
            "ownerId": owner_id,
            "correlationId": correlation_id,
            "jobId": job_id,
            "taskId": task_id,
            "acquiredAtMs": int(time.time() * 1000),
        }
        while True:
            try:
                raw = self._acquire_script(
                    keys=[self._key(model_profile)],
                    args=[
                        int(time.time() * 1000),
                        max(1, max_concurrency),
                        lease_id,
                        json.dumps(owner),
                        ttl,
                    ],
                )
                payload = json.loads(raw)
            except Exception as exc:  # noqa: BLE001
                LOG.error("event=CAPACITY_PROVIDER_UNAVAILABLE error=%s", exc)
                if self._failure_policy == "FAIL_CLOSED":
                    return "PROVIDER_UNAVAILABLE", None, (time.monotonic() - started) * 1000.0
                raise
            status = payload.get("status")
            if status == "ACQUIRED":
                lease = CapacityLease(
                    lease_id=lease_id,
                    model_profile=model_profile,
                    owner_id=owner_id,
                    generation=int(payload.get("generation", 1)),
                    acquired_at_ms=owner["acquiredAtMs"],
                    expires_at_ms=int(payload["expiresAtMs"]),
                )
                LOG.info(
                    "event=CAPACITY_ACQUIRED profile=%s leaseId=%s generation=%s active=%s max=%s correlation_id=%s",
                    model_profile,
                    lease.lease_id,
                    lease.generation,
                    payload.get("active"),
                    payload.get("max"),
                    correlation_id,
                )
                return "ACQUIRED", lease, (time.monotonic() - started) * 1000.0
            if time.monotonic() >= deadline or wait_timeout_seconds <= 0:
                return "CAPACITY_FULL" if wait_timeout_seconds <= 0 else "WAIT_TIMEOUT", None, (
                    time.monotonic() - started
                ) * 1000.0
            time.sleep(poll_interval_seconds)

    def heartbeat(self, lease: CapacityLease, lease_ttl_ms: int | None = None) -> str:
        ttl = lease_ttl_ms or self._default_lease_ttl_ms
        try:
            raw = self._heartbeat_script(
                keys=[self._key(lease.model_profile)],
                args=[int(time.time() * 1000), lease.lease_id, lease.generation, ttl],
            )
            return json.loads(raw).get("status", "PROVIDER_UNAVAILABLE")
        except Exception as exc:  # noqa: BLE001
            LOG.error("event=CAPACITY_HEARTBEAT_FAILED leaseId=%s error=%s", lease.lease_id, exc)
            return "PROVIDER_UNAVAILABLE"

    def release(self, lease: CapacityLease | None) -> str:
        if lease is None:
            return "NOT_FOUND"
        try:
            raw = self._release_script(
                keys=[self._key(lease.model_profile)],
                args=[lease.lease_id, lease.generation],
            )
            status = json.loads(raw).get("status", "PROVIDER_UNAVAILABLE")
            LOG.info(
                "event=CAPACITY_RELEASE profile=%s leaseId=%s generation=%s status=%s",
                lease.model_profile,
                lease.lease_id,
                lease.generation,
                status,
            )
            return status
        except Exception as exc:  # noqa: BLE001
            LOG.error("event=CAPACITY_RELEASE_FAILED leaseId=%s error=%s", lease.lease_id, exc)
            return "PROVIDER_UNAVAILABLE"

    def snapshot(self, model_profile: str) -> dict[str, Any]:
        try:
            raw = self._snapshot_script(
                keys=[self._key(model_profile)],
                args=[int(time.time() * 1000)],
            )
            return json.loads(raw)
        except Exception as exc:  # noqa: BLE001
            LOG.error("event=CAPACITY_SNAPSHOT_FAILED profile=%s error=%s", model_profile, exc)
            return {"active": -1, "error": "PROVIDER_UNAVAILABLE"}

    def health_probe(self) -> dict[str, Any]:
        """Low-cost acquire/release on an isolated health profile (not product capacity)."""
        health_profile = "__ORCHESTRATOR_HEALTH__"
        try:
            self._redis.ping()
            status, lease, _wait = self.acquire(
                model_profile=health_profile,
                max_concurrency=1,
                owner_id="orchestrator-health-probe",
                correlation_id="health-probe",
                lease_ttl_ms=2_000,
                wait_timeout_seconds=0,
            )
            if status != "ACQUIRED" or lease is None:
                return {
                    "capacityProvider": self.provider_name,
                    "failurePolicy": self._failure_policy,
                    "providerReachable": True,
                    "leaseOperationsHealthy": False,
                    "detail": f"acquire_status={status}",
                }
            release_status = self.release(lease)
            healthy = release_status in {"RELEASED", "ALREADY_RELEASED"}
            return {
                "capacityProvider": self.provider_name,
                "failurePolicy": self._failure_policy,
                "providerReachable": True,
                "leaseOperationsHealthy": healthy,
                "detail": f"release_status={release_status}",
            }
        except Exception as exc:  # noqa: BLE001
            LOG.error("event=CAPACITY_HEALTH_PROBE_FAILED error=%s", exc)
            return {
                "capacityProvider": self.provider_name,
                "failurePolicy": self._failure_policy,
                "providerReachable": False,
                "leaseOperationsHealthy": False,
                "detail": str(exc),
            }


class FailClosedCapacityManager:
    """Used when Redis is required but unreachable at startup/runtime."""

    failure_policy = "FAIL_CLOSED"
    provider_name = "redis-unavailable"

    def acquire(self, **kwargs: Any) -> tuple[str, CapacityLease | None, float]:
        LOG.error("event=CAPACITY_PROVIDER_UNAVAILABLE reason=FAIL_CLOSED_STUB")
        return "PROVIDER_UNAVAILABLE", None, 0.0

    def heartbeat(self, lease: CapacityLease, lease_ttl_ms: int | None = None) -> str:
        return "PROVIDER_UNAVAILABLE"

    def release(self, lease: CapacityLease | None) -> str:
        return "PROVIDER_UNAVAILABLE"

    def snapshot(self, model_profile: str) -> dict[str, Any]:
        return {"active": -1, "error": "PROVIDER_UNAVAILABLE"}

    def health_probe(self) -> dict[str, Any]:
        return {
            "capacityProvider": self.provider_name,
            "failurePolicy": self.failure_policy,
            "providerReachable": False,
            "leaseOperationsHealthy": False,
            "detail": "FAIL_CLOSED_STUB",
        }


def build_capacity_manager_from_env() -> RedisModelCapacityManager | FailClosedCapacityManager | None:
    mode = os.getenv("MODEL_CAPACITY_PROVIDER", "redis").strip().lower()
    if mode in {"none", "local", "process-local"}:
        LOG.warning(
            "event=MODEL_CAPACITY_PROVIDER_LOCAL mode=%s "
            "note=not_valid_for_multi_instance_production",
            mode,
        )
        return None
    if mode != "redis":
        raise RuntimeError(f"Unsupported MODEL_CAPACITY_PROVIDER={mode}")
    host = os.getenv("REDIS_HOST", "redis")
    port = os.getenv("REDIS_PORT", "6379")
    password = os.getenv("REDIS_PASSWORD", "")
    db = os.getenv("REDIS_DB", "0")
    if password:
        url = f"redis://:{password}@{host}:{port}/{db}"
    else:
        url = f"redis://{host}:{port}/{db}"
    failure = os.getenv("MODEL_CAPACITY_FAILURE_POLICY", "FAIL_CLOSED")
    ttl = int(os.getenv("MODEL_CAPACITY_LEASE_TTL_MS", "120000"))
    try:
        return RedisModelCapacityManager(
            url,
            default_lease_ttl_ms=ttl,
            failure_policy=failure,
        )
    except Exception as exc:  # noqa: BLE001
        LOG.error("event=CAPACITY_PROVIDER_INIT_FAILED error=%s policy=%s", exc, failure)
        if failure.upper() == "FAIL_CLOSED":
            return FailClosedCapacityManager()
        raise
