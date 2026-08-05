"""Ambiguity prioritization and structured auto-resolution."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal

from .measurable_fields import (
    MeasurableFields,
    extract_measurable_fields,
    merge_attributes,
)

AmbiguityPriority = Literal["HIGH", "MEDIUM", "LOW"]
AUTO_RESOLVE_MIN_CONF = 0.85

_TECH_SEC = {"TECHNICAL", "SECURITY", "TECH", "SEC"}
_MUST = {"MUST", "MANDATORY", "SHALL", "REQUIRED"}


@dataclass
class AmbiguityCandidate:
    requirement_id: str
    text: str
    category: str = "OTHER"
    obligation_level: str = "INFORMATIONAL"
    attributes: dict[str, Any] = field(default_factory=dict)
    missing_fields: list[str] = field(default_factory=list)
    priority: AmbiguityPriority = "MEDIUM"
    suggested_fields: dict[str, Any] = field(default_factory=dict)
    confidence: float = 0.0
    status: str = "CANDIDATE"  # CANDIDATE | RESOLVED_STRUCTURED


def _missing_measurable(attrs: dict[str, Any]) -> list[str]:
    keys = ("measurement", "operator", "testCondition", "acceptanceThreshold")
    missing: list[str] = []
    for key in keys:
        val = attrs.get(key)
        if val is None or (isinstance(val, str) and not str(val).strip()):
            missing.append(
                {
                    "measurement": "missingMeasurement",
                    "operator": "missingOperator",
                    "testCondition": "missingTestCondition",
                    "acceptanceThreshold": "missingAcceptanceThreshold",
                }[key]
            )
    return missing


def _priority_for(req: dict[str, Any], missing: list[str], fields: MeasurableFields) -> AmbiguityPriority:
    category = str(req.get("category") or req.get("primaryCategory") or "OTHER").upper()
    obligation = str(
        req.get("obligationLevel")
        or req.get("modality")
        or req.get("modalityCode")
        or ""
    ).upper()
    missing_threshold = "missingAcceptanceThreshold" in missing
    if category in _TECH_SEC and obligation in _MUST and missing_threshold:
        return "HIGH"
    if fields.kind == "presence" or fields.confidence < 0.7:
        return "MEDIUM" if obligation in _MUST else "LOW"
    if missing:
        return "MEDIUM" if obligation in _MUST else "LOW"
    return "LOW"


def apply_auto_resolution(
    requirements: list[dict[str, Any]],
    *,
    min_confidence: float = AUTO_RESOLVE_MIN_CONF,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """
    Enrich measurable attributes; auto-resolve when confidence is high and fields complete.

    Returns (updated_requirements, events).
    """
    updated: list[dict[str, Any]] = []
    events: list[dict[str, Any]] = []
    for raw in requirements or []:
        req = dict(raw)
        text = str(req.get("text") or req.get("requirementText") or req.get("normalizedText") or "")
        attrs = dict(req.get("attributes") or req.get("attributesJson") or {})
        fields = extract_measurable_fields(text)
        # Only commit structured fields when confidence clears auto-resolve threshold.
        # Low-confidence qualitative hits stay as suggestedFields for expert triage.
        if fields.kind != "none" and fields.confidence >= min_confidence and fields.fields_complete():
            merged = merge_attributes(attrs, fields)
            req["attributes"] = merged
            req["ambiguityStatus"] = "RESOLVED_STRUCTURED"
            events.append(
                {
                    "type": "AMBIGUITY_AUTO_RESOLVED",
                    "requirementId": req.get("requirementId") or req.get("id"),
                    "status": "RESOLVED_STRUCTURED",
                    "fields": {
                        k: merged.get(k)
                        for k in (
                            "measurement",
                            "operator",
                            "testCondition",
                            "acceptanceThreshold",
                        )
                    },
                    "confidence": fields.confidence,
                }
            )
        else:
            req["attributes"] = attrs
            req["ambiguityStatus"] = req.get("ambiguityStatus") or "CANDIDATE"
            if fields.kind != "none":
                suggested = fields.as_attributes()
                req["suggestedFields"] = suggested
                events.append(
                    {
                        "type": "AMBIGUITY_FIELDS_SUGGESTED",
                        "requirementId": req.get("requirementId") or req.get("id"),
                        "suggestedFields": suggested,
                        "confidence": fields.confidence,
                    }
                )
        updated.append(req)
    return updated, events


def prioritize_ambiguities(
    requirements: list[dict[str, Any]],
) -> list[AmbiguityCandidate]:
    """Build HIGH|MEDIUM|LOW queue for requirements still missing measurable structure."""
    queue: list[AmbiguityCandidate] = []
    for req in requirements or []:
        if str(req.get("ambiguityStatus") or "") == "RESOLVED_STRUCTURED":
            continue
        text = str(req.get("text") or req.get("requirementText") or "")
        attrs = dict(req.get("attributes") or {})
        fields = extract_measurable_fields(text)
        suggested = dict(req.get("suggestedFields") or fields.as_attributes())
        missing = _missing_measurable(attrs)
        if not missing:
            continue
        priority = _priority_for(req, missing, fields)
        queue.append(
            AmbiguityCandidate(
                requirement_id=str(req.get("requirementId") or req.get("id") or ""),
                text=text,
                category=str(req.get("category") or "OTHER"),
                obligation_level=str(req.get("obligationLevel") or req.get("modality") or ""),
                attributes=attrs,
                missing_fields=missing,
                priority=priority,
                suggested_fields={
                    k: suggested[k]
                    for k in (
                        "measurement",
                        "operator",
                        "acceptanceThreshold",
                        "testCondition",
                    )
                    if k in suggested and suggested[k] is not None
                },
                confidence=float(fields.confidence or suggested.get("measurableConfidence") or 0),
                status="CANDIDATE",
            )
        )
    order = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
    queue.sort(key=lambda c: (order.get(c.priority, 9), -c.confidence, c.requirement_id))
    return queue
