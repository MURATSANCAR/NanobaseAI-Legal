"""Unit tests for BALANCED generation timeout env override (no Redis / FastAPI import)."""
from __future__ import annotations

import os

import pytest

from timeout_config import (
    TimeoutDeploymentView,
    apply_balanced_timeout_seconds,
    map_timeouts,
    parse_iso8601_duration_seconds,
)


def test_parse_iso8601_duration_seconds_pt720s():
    assert parse_iso8601_duration_seconds("PT720S") == 720.0
    assert parse_iso8601_duration_seconds("pt12m") == 720.0
    assert parse_iso8601_duration_seconds("PT1H") == 3600.0


def test_parse_iso8601_duration_rejects_invalid():
    with pytest.raises(ValueError):
        parse_iso8601_duration_seconds("720")
    with pytest.raises(ValueError):
        parse_iso8601_duration_seconds("PT")
    with pytest.raises(ValueError):
        parse_iso8601_duration_seconds("")


def test_balanced_override_does_not_touch_fast(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT", "PT720S")
    assert apply_balanced_timeout_seconds("FAST", 300) == 300
    assert apply_balanced_timeout_seconds("BALANCED", 600) == 720


def test_balanced_override_absent_leaves_json_value(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT", raising=False)
    assert apply_balanced_timeout_seconds("BALANCED", 600) == 600


def test_map_timeouts_applies_only_balanced(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT", "PT720S")
    mapped = map_timeouts(
        (
            TimeoutDeploymentView("FAST", 300),
            TimeoutDeploymentView("BALANCED", 600),
        )
    )
    assert mapped[0].timeout_seconds == 300
    assert mapped[1].timeout_seconds == 720


def test_compose_topology_docs_reference_both_files():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    ops = os.path.join(root, "docs", "LEGAL-ORCHESTRATOR-OPERATIONS.md")
    assert os.path.isfile(ops)
    text = open(ops, encoding="utf-8").read()
    assert "-p specai-legal" in text
    assert "compose.yaml" in text
    assert "compose.easymeeting.yaml" in text
    assert "ai-orchestrator" in text
    assert "AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT=PT720S" in text
    assert "FAIL_CLOSED" in text
