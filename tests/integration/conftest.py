"""Pytest hooks for integration suite."""
from __future__ import annotations

import os

import pytest


def pytest_configure(config):
    config.addinivalue_line(
        "markers",
        "integration: real Redis/pgvector integration gates",
    )


def pytest_report_header(config):
    redis_req = os.getenv("INTEGRATION_REQUIRE_REDIS", "0")
    pg_req = os.getenv("INTEGRATION_REQUIRE_PG", "0")
    if redis_req in {"0", ""} and pg_req in {"0", ""}:
        return ["INTEGRATION NOT EXECUTED (set INTEGRATION_REQUIRE_REDIS/PG=1 for skip=0 gates)"]
    return [
        f"INTEGRATION_REQUIRE_REDIS={redis_req}",
        f"INTEGRATION_REQUIRE_PG={pg_req}",
    ]
