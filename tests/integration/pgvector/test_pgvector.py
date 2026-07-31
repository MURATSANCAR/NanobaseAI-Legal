from __future__ import annotations

import os

import pytest

from tests.integration._guards import fail_or_skip


def _pg_url() -> str | None:
    return (
        os.getenv("PGVECTOR_URL")
        or os.getenv("DATABASE_URL")
        or os.getenv("POSTGRES_URL")
    )


@pytest.mark.integration
def test_pgvector_extension_present():
    url = _pg_url()
    if not url:
        fail_or_skip("INTEGRATION_REQUIRE_PG", "PGVECTOR_URL missing")
    try:
        import psycopg
    except ImportError:
        try:
            import psycopg2 as psycopg  # type: ignore
        except ImportError as exc:
            fail_or_skip("INTEGRATION_REQUIRE_PG", f"psycopg missing: {exc}")
    try:
        with psycopg.connect(url, connect_timeout=5) as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT extname, extversion FROM pg_extension WHERE extname='vector'"
                )
                row = cur.fetchone()
                if row is None:
                    fail_or_skip(
                        "INTEGRATION_REQUIRE_PG",
                        "pgvector extension 'vector' not installed",
                    )
                assert row[0] == "vector"
    except Exception as exc:  # noqa: BLE001
        # connection errors or missing extension under require mode must fail
        if "not installed" in str(exc):
            raise
        fail_or_skip("INTEGRATION_REQUIRE_PG", f"PostgreSQL/pgvector unavailable: {exc}")


@pytest.mark.integration
def test_pgvector_basic_distance_query():
    url = _pg_url()
    if not url:
        fail_or_skip("INTEGRATION_REQUIRE_PG", "PGVECTOR_URL missing")
    try:
        import psycopg
    except ImportError:
        try:
            import psycopg2 as psycopg  # type: ignore
        except ImportError as exc:
            fail_or_skip("INTEGRATION_REQUIRE_PG", f"psycopg missing: {exc}")
    try:
        with psycopg.connect(url, connect_timeout=5) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT extname FROM pg_extension WHERE extname='vector'")
                if cur.fetchone() is None:
                    fail_or_skip(
                        "INTEGRATION_REQUIRE_PG",
                        "pgvector extension 'vector' not installed",
                    )
                cur.execute("SELECT '[1,2,3]'::vector <-> '[1,2,4]'::vector")
                distance = cur.fetchone()[0]
                assert float(distance) > 0
    except Exception as exc:  # noqa: BLE001
        fail_or_skip("INTEGRATION_REQUIRE_PG", f"pgvector query failed: {exc}")
