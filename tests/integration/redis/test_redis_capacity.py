from __future__ import annotations

import os
import uuid

import pytest

from tests.integration._guards import fail_or_skip, require_flag


def _redis_url() -> str | None:
    url = os.getenv("REDIS_URL") or os.getenv("TEST_REDIS_URL")
    if url:
        return url
    host = os.getenv("REDIS_HOST")
    if not host:
        return None
    password = os.getenv("REDIS_PASSWORD", "")
    port = os.getenv("REDIS_PORT", "6379")
    return f"redis://:{password}@{host}:{port}/15" if password else f"redis://{host}:{port}/15"


@pytest.mark.integration
def test_redis_ping_required():
    url = _redis_url()
    if not url:
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", "REDIS_URL/REDIS_HOST missing")
    try:
        import redis
    except ImportError as exc:
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", f"redis package missing: {exc}")
    try:
        client = redis.Redis.from_url(url, socket_connect_timeout=3, socket_timeout=3)
        assert client.ping() is True
    except Exception as exc:  # noqa: BLE001
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", f"Redis unavailable: {exc}")


@pytest.mark.integration
def test_redis_capacity_lease_roundtrip():
    url = _redis_url()
    if not url:
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", "REDIS_URL/REDIS_HOST missing")
    import sys
    from pathlib import Path

    orch = Path(__file__).resolve().parents[2] / "services" / "ai-orchestrator"
    sys.path.insert(0, str(orch))
    from capacity import RedisModelCapacityManager

    mgr = RedisModelCapacityManager(
        url,
        key_prefix=f"specai:integ-capacity:{uuid.uuid4().hex[:8]}",
        default_lease_ttl_ms=2_000,
        failure_policy="FAIL_CLOSED",
    )
    status, lease, _ = mgr.acquire(
        model_profile="BALANCED",
        max_concurrency=1,
        owner_id="integ",
        correlation_id="integ-1",
        wait_timeout_seconds=0,
    )
    assert status == "ACQUIRED"
    assert lease is not None
    assert mgr.release(lease) == "RELEASED"
    if not require_flag("INTEGRATION_REQUIRE_REDIS"):
        # Local soft mode still executed here because Redis was available.
        pass
