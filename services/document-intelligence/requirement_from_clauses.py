"""Deterministic requirement extraction from clauses (Turkish tender signals)."""

from __future__ import annotations

import hashlib
import re
from typing import Any

from requirement_categorizer_v2 import categorize_many, categorize_requirement
from textambiguity import apply_auto_resolution, prioritize_ambiguities

MUST_RE = re.compile(
    r"\b("
    r"zorunlu|mecburi|şarttır|zorundadır|edilmelidir|yapılacaktır|"
    r"sağlanacaktır|teslim\s+edilecektir|yerine\s+getirilecektir|"
    r"olacaktır|bulunacaktır|destekleyecektir|edilecektir|"
    r"must|shall|required|mandatory"
    r")\b",
    re.IGNORECASE,
)
SHOULD_RE = re.compile(
    r"\b("
    r"tercihen|tercih\s+edilir|önerilir|tavsiye|istenir|"
    r"should|preferably|recommended"
    r")\b",
    re.IGNORECASE,
)


def _norm(text: str) -> str:
    return " ".join((text or "").split())


def _hash(text: str) -> str:
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()


def _obligation(text: str) -> str:
    if MUST_RE.search(text or ""):
        return "MUST"
    if SHOULD_RE.search(text or ""):
        return "SHOULD"
    return "INFORMATIONAL"


def _category(text: str, *, title: str = "") -> str:
    return categorize_requirement(text, title=title)


def requirements_from_clauses(clauses: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Build deterministic requirements from clause texts."""
    requirements: list[dict[str, Any]] = []
    for index, clause in enumerate(clauses or []):
        raw = str(clause.get("rawText") or "")
        title = str(clause.get("title") or "")
        body = _norm(f"{title} {raw}")
        if len(body) < 40:
            continue
        obligation = _obligation(body)
        if obligation == "INFORMATIONAL" and len(body) < 120:
            continue
        source_id = str(clause.get("sourceId") or f"clause-{index}")
        req_text = raw.strip()[:4000] or title
        requirements.append(
            {
                "requirementId": f"req-{_hash(source_id + '|' + req_text)[:16]}",
                "sourceClauseIds": [source_id],
                "title": (title or f"Requirement {index + 1}")[:500],
                "text": req_text,
                "normalizedText": _norm(req_text),
                "category": _category(body, title=title),
                "obligationLevel": obligation,
                "pageStart": clause.get("pageStart"),
                "pageEnd": clause.get("pageEnd"),
                "contentHash": _hash(req_text),
                "metadata": {
                    "extractor": "requirement_from_clauses",
                    "categorizer": "requirement_categorizer_v2",
                    "clauseNumber": clause.get("clauseNumber"),
                },
            }
        )
    return categorize_many(requirements)


def attach_requirements_to_result(result: dict[str, Any]) -> dict[str, Any]:
    """Attach requirements list into an existing parse result (non-destructive)."""
    enriched = dict(result)
    clauses = list(enriched.get("clauses") or [])
    requirements = requirements_from_clauses(clauses)
    requirements, ambiguity_events = apply_auto_resolution(requirements)
    ambiguity_queue = prioritize_ambiguities(requirements)
    enriched["requirements"] = requirements
    metadata = dict(enriched.get("metadata") or {})
    metadata["requirementCount"] = len(requirements)
    metadata["requirementExtractor"] = "requirement_from_clauses"
    metadata["requirementCategorizer"] = "requirement_categorizer_v2"
    metadata["ambiguityAutoResolved"] = sum(
        1 for e in ambiguity_events if e.get("type") == "AMBIGUITY_AUTO_RESOLVED"
    )
    metadata["ambiguityCandidateCount"] = len(ambiguity_queue)
    metadata["ambiguityHighCount"] = sum(1 for c in ambiguity_queue if c.priority == "HIGH")
    enriched["metadata"] = metadata
    enriched["ambiguityEvents"] = ambiguity_events
    enriched["ambiguityQueue"] = [
        {
            "requirementId": c.requirement_id,
            "priority": c.priority,
            "missingFields": c.missing_fields,
            "suggestedFields": c.suggested_fields,
            "confidence": c.confidence,
            "status": c.status,
        }
        for c in ambiguity_queue
    ]
    return enriched
