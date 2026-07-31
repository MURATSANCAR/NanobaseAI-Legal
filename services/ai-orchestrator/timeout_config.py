"""BALANCED generation timeout helpers (ISO-8601 env override)."""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Optional, Sequence, Tuple

LOG = logging.getLogger("specai.ai_orchestrator")


@dataclass(frozen=True)
class TimeoutDeploymentView:
    profile: str
    timeout_seconds: float


def parse_iso8601_duration_seconds(value: str) -> float:
    """Parse a subset of ISO-8601 durations used in ops env (e.g. PT720S, PT12M)."""
    raw = (value or "").strip().upper()
    if not raw.startswith("PT") or len(raw) < 3:
        raise ValueError(f"Unsupported duration: {value!r}")
    body = raw[2:]
    total = 0.0
    number = ""
    for char in body:
        if char.isdigit() or char == ".":
            number += char
            continue
        if not number:
            raise ValueError(f"Unsupported duration: {value!r}")
        amount = float(number)
        number = ""
        if char == "H":
            total += amount * 3600.0
        elif char == "M":
            total += amount * 60.0
        elif char == "S":
            total += amount
        else:
            raise ValueError(f"Unsupported duration: {value!r}")
    if number or total <= 0:
        raise ValueError(f"Unsupported duration: {value!r}")
    return total


def balanced_generation_timeout_override_seconds() -> Optional[float]:
    raw = os.getenv("AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT", "").strip()
    if not raw:
        return None
    return parse_iso8601_duration_seconds(raw)


def apply_balanced_timeout_seconds(
    profile: str,
    timeout_seconds: float,
) -> float:
    """Return timeout for a deployment profile, applying BALANCED env override when set."""
    override = balanced_generation_timeout_override_seconds()
    if override is None:
        return timeout_seconds
    if profile.strip().upper() != "BALANCED":
        return timeout_seconds
    LOG.info(
        "event=BALANCED_GENERATION_TIMEOUT_OVERRIDE seconds=%.1f "
        "source=AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT",
        override,
    )
    return override


def map_timeouts(
    deployments: Sequence[TimeoutDeploymentView],
) -> Tuple[TimeoutDeploymentView, ...]:
    return tuple(
        TimeoutDeploymentView(
            profile=item.profile,
            timeout_seconds=apply_balanced_timeout_seconds(
                item.profile, item.timeout_seconds
            ),
        )
        for item in deployments
    )
