"""Prometheus metrics for document-intelligence parser routing.

Works with or without prometheus_client installed (no-op fallback).
"""

from __future__ import annotations

import logging
import os
import time
from contextlib import contextmanager
from typing import Any, Generator

logger = logging.getLogger("specai.document.parser_metrics")

try:
    from prometheus_client import REGISTRY, Counter, Gauge, Histogram, generate_latest
    from prometheus_client.exposition import CONTENT_TYPE_LATEST

    _PROM_AVAILABLE = True
except ImportError:  # pragma: no cover
    REGISTRY = None  # type: ignore[assignment]
    Counter = Gauge = Histogram = None  # type: ignore[assignment]
    generate_latest = None  # type: ignore[assignment]
    CONTENT_TYPE_LATEST = "text/plain; version=0.0.4; charset=utf-8"
    _PROM_AVAILABLE = False


def _short_circuit_enabled() -> bool:
    return os.getenv("PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT", "true").lower() in {
        "1",
        "true",
        "yes",
    }


if _PROM_AVAILABLE:
    PARSER_PATH_TOTAL = Counter(
        "specai_parser_path_total",
        "Parser route selections",
        ["path", "pdf_type", "outcome"],
    )
    PARSER_DURATION_SECONDS = Histogram(
        "specai_parser_duration_seconds",
        "Parser end-to-end duration seconds",
        ["path", "pdf_type"],
        buckets=(
            0.05,
            0.1,
            0.25,
            0.5,
            1.0,
            2.5,
            5.0,
            10.0,
            30.0,
            60.0,
            120.0,
            300.0,
            600.0,
            1800.0,
            3600.0,
        ),
    )
    PARSER_CLAUSES_TOTAL = Counter(
        "specai_parser_clauses_total",
        "Clauses produced by parser path",
        ["path"],
    )
    PARSER_TABLES_TOTAL = Counter(
        "specai_parser_tables_total",
        "Tables produced by parser path",
        ["path"],
    )
    PARSER_PAGES_TOTAL = Counter(
        "specai_parser_pages_total",
        "Pages processed by parser path",
        ["path", "pdf_type"],
    )
    PARSER_SHORT_CIRCUIT_ENABLED = Gauge(
        "specai_parser_short_circuit_enabled",
        "1 when markdown short-circuit feature flag is enabled",
    )
    PARSER_GUARD_TOTAL = Counter(
        "specai_parser_guard_total",
        "Pre-parse guard rejections by error code",
        ["error_code"],
    )
    PARSER_SHORT_CIRCUIT_ENABLED.set(1.0 if _short_circuit_enabled() else 0.0)
else:  # pragma: no cover
    PARSER_PATH_TOTAL = None
    PARSER_DURATION_SECONDS = None
    PARSER_CLAUSES_TOTAL = None
    PARSER_TABLES_TOTAL = None
    PARSER_PAGES_TOTAL = None
    PARSER_SHORT_CIRCUIT_ENABLED = None
    PARSER_GUARD_TOTAL = None


def record_guard(error_code: str) -> None:
    if PARSER_GUARD_TOTAL is None:
        return
    try:
        PARSER_GUARD_TOTAL.labels(error_code=(error_code or "UNKNOWN")).inc()
    except Exception:  # noqa: BLE001
        logger.exception("failed to record guard metric")


def refresh_short_circuit_gauge() -> None:
    if PARSER_SHORT_CIRCUIT_ENABLED is not None:
        PARSER_SHORT_CIRCUIT_ENABLED.set(1.0 if _short_circuit_enabled() else 0.0)


def record_parse(
    path: str,
    pdf_type: str | None,
    duration_seconds: float,
    *,
    page_count: int = 0,
    clause_count: int = 0,
    table_count: int = 0,
    outcome: str = "success",
) -> None:
    """Record one completed (or failed) parse attempt."""
    pdf = (pdf_type or "unknown").strip() or "unknown"
    route = (path or "unknown").strip() or "unknown"
    result = (outcome or "success").strip() or "success"
    if not _PROM_AVAILABLE:
        logger.debug(
            "parser_metrics noop",
            extra={
                "path": route,
                "pdf_type": pdf,
                "outcome": result,
                "duration_seconds": duration_seconds,
                "page_count": page_count,
                "clause_count": clause_count,
                "table_count": table_count,
            },
        )
        return
    try:
        refresh_short_circuit_gauge()
        PARSER_PATH_TOTAL.labels(path=route, pdf_type=pdf, outcome=result).inc()
        PARSER_DURATION_SECONDS.labels(path=route, pdf_type=pdf).observe(
            max(0.0, float(duration_seconds))
        )
        if page_count:
            PARSER_PAGES_TOTAL.labels(path=route, pdf_type=pdf).inc(int(page_count))
        if clause_count:
            PARSER_CLAUSES_TOTAL.labels(path=route).inc(int(clause_count))
        if table_count:
            PARSER_TABLES_TOTAL.labels(path=route).inc(int(table_count))
    except Exception:  # noqa: BLE001 – metrics must never break parsing
        logger.exception("failed to record parser metrics")


@contextmanager
def track_parse(
    path: str, pdf_type: str | None = None
) -> Generator[dict[str, Any], None, None]:
    """Context manager yielding a mutable metrics context dict.

    Callers set page_count / clause_count / table_count / outcome before exit.
    """
    mctx: dict[str, Any] = {
        "page_count": 0,
        "clause_count": 0,
        "table_count": 0,
        "outcome": "success",
    }
    started = time.perf_counter()
    try:
        yield mctx
    except Exception:
        if mctx.get("outcome") == "success":
            mctx["outcome"] = "error"
        record_parse(
            path,
            pdf_type,
            time.perf_counter() - started,
            page_count=int(mctx.get("page_count") or 0),
            clause_count=int(mctx.get("clause_count") or 0),
            table_count=int(mctx.get("table_count") or 0),
            outcome=str(mctx.get("outcome") or "error"),
        )
        raise
    else:
        record_parse(
            path,
            pdf_type,
            time.perf_counter() - started,
            page_count=int(mctx.get("page_count") or 0),
            clause_count=int(mctx.get("clause_count") or 0),
            table_count=int(mctx.get("table_count") or 0),
            outcome=str(mctx.get("outcome") or "success"),
        )


def metrics_payload() -> tuple[bytes, str]:
    """Return (body, content_type) for GET /metrics."""
    refresh_short_circuit_gauge()
    if not _PROM_AVAILABLE or generate_latest is None:
        body = (
            b"# prometheus_client not installed\n"
            b"# HELP specai_parser_metrics_available 0\n"
            b"# TYPE specai_parser_metrics_available gauge\n"
            b"specai_parser_metrics_available 0\n"
        )
        return body, "text/plain; charset=utf-8"
    return generate_latest(REGISTRY), CONTENT_TYPE_LATEST


def prometheus_available() -> bool:
    return _PROM_AVAILABLE
