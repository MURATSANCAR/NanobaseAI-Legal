from __future__ import annotations

import os
import uuid

import pytest

from tests.integration._guards import fail_or_skip


@pytest.mark.integration
def test_conversation_state_roundtrip_via_redis():
    """Minimal conversation-state style key write/read on Redis."""
    url = os.getenv("REDIS_URL") or os.getenv("TEST_REDIS_URL")
    if not url:
        host = os.getenv("REDIS_HOST")
        if not host:
            fail_or_skip("INTEGRATION_REQUIRE_REDIS", "REDIS_URL/REDIS_HOST missing")
        password = os.getenv("REDIS_PASSWORD", "")
        port = os.getenv("REDIS_PORT", "6379")
        url = f"redis://:{password}@{host}:{port}/15" if password else f"redis://{host}:{port}/15"
    try:
        import redis
    except ImportError as exc:
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", f"redis package missing: {exc}")
    try:
        client = redis.Redis.from_url(url, decode_responses=True, socket_connect_timeout=3)
        assert client.ping() is True
        key = f"specai:integ:conversation:{uuid.uuid4().hex}"
        client.hset(key, mapping={"state": "ACTIVE", "turn": "1"})
        client.expire(key, 30)
        assert client.hget(key, "state") == "ACTIVE"
        client.delete(key)
    except Exception as exc:  # noqa: BLE001
        fail_or_skip("INTEGRATION_REQUIRE_REDIS", f"conversation-state Redis failed: {exc}")
