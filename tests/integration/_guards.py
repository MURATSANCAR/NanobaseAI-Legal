"""Integration test helpers: fail-closed when INTEGRATION_REQUIRE_* is set."""
from __future__ import annotations

import os


def require_flag(name: str) -> bool:
    return os.getenv(name, "0").strip() in {"1", "true", "TRUE", "yes", "YES"}


def fail_or_skip(require_env: str, message: str) -> None:
    import pytest

    if require_flag(require_env):
        pytest.fail(message)
    pytest.skip(f"INTEGRATION NOT EXECUTED: {message}")
