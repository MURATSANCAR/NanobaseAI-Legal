"""Unit tests for Redis Lua capacity leases (requires REDIS_URL or skips)."""
from __future__ import annotations

import os
import time
import uuid

import pytest

from capacity import RedisModelCapacityManager


@pytest.fixture(scope="module")
def manager():
    url = os.getenv("REDIS_URL") or os.getenv("TEST_REDIS_URL")
    if not url:
        host = os.getenv("REDIS_HOST")
        if not host:
            if os.getenv("INTEGRATION_REQUIRE_REDIS", "0").strip() in {"1", "true", "TRUE"}:
                pytest.fail("REDIS_URL/REDIS_HOST not set under INTEGRATION_REQUIRE_REDIS=1")
            pytest.skip("INTEGRATION NOT EXECUTED: REDIS_URL/REDIS_HOST not set")
        password = os.getenv("REDIS_PASSWORD", "")
        port = os.getenv("REDIS_PORT", "6379")
        url = f"redis://:{password}@{host}:{port}/15" if password else f"redis://{host}:{port}/15"
    mgr = RedisModelCapacityManager(
        url,
        key_prefix=f"specai:test-capacity:{uuid.uuid4().hex[:8]}",
        default_lease_ttl_ms=2_000,
        failure_policy="FAIL_CLOSED",
    )
    yield mgr


def test_acquire_capacity_full_and_release(manager: RedisModelCapacityManager):
    profile = "BALANCED"
    s1, l1, _ = manager.acquire(
        model_profile=profile,
        max_concurrency=1,
        owner_id="owner-a",
        correlation_id="c1",
        wait_timeout_seconds=0,
    )
    assert s1 == "ACQUIRED"
    assert l1 is not None
    s2, l2, _ = manager.acquire(
        model_profile=profile,
        max_concurrency=1,
        owner_id="owner-b",
        correlation_id="c2",
        wait_timeout_seconds=0,
    )
    assert s2 == "CAPACITY_FULL"
    assert l2 is None
    assert manager.release(l1) == "RELEASED"
    assert manager.release(l1) == "ALREADY_RELEASED"
    s3, l3, _ = manager.acquire(
        model_profile=profile,
        max_concurrency=1,
        owner_id="owner-b",
        correlation_id="c3",
        wait_timeout_seconds=0,
    )
    assert s3 == "ACQUIRED"
    assert manager.release(l3) == "RELEASED"


def test_lease_expires_without_release(manager: RedisModelCapacityManager):
    profile = "FAST"
    s1, l1, _ = manager.acquire(
        model_profile=profile,
        max_concurrency=1,
        owner_id="owner-a",
        correlation_id="c1",
        lease_ttl_ms=800,
        wait_timeout_seconds=0,
    )
    assert s1 == "ACQUIRED"
    time.sleep(1.1)
    snap = manager.snapshot(profile)
    assert snap["active"] == 0
    s2, l2, _ = manager.acquire(
        model_profile=profile,
        max_concurrency=1,
        owner_id="owner-b",
        correlation_id="c2",
        wait_timeout_seconds=0,
    )
    assert s2 == "ACQUIRED"
    assert l2 is not None
    assert l2.lease_id != l1.lease_id
    assert manager.release(l2) == "RELEASED"
    # stale release must not go negative
    assert manager.release(l1) in {"ALREADY_RELEASED", "STALE_LEASE", "NOT_FOUND"}
