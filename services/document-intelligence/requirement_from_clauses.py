"""Deterministic requirement extraction from clauses (Turkish tender signals)."""

from __future__ import annotations

import hashlib
import re
from typing import Any

MUST_RE = re.compile(
    r"\b("
    r"zorunlu|mecburi|şarttır|zorundadır|edilmelidir|yapılacaktır|"
    r"sağlanacaktır|teslim\s+edilecektir|yerine\s+getirilecektir|"
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
CATEGORY_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("TECHNICAL", re.compile(r"\b(teknik|yazılım|donanım|entegrasyon|api|sistem|sla)\b", re.I)),
    ("SECURITY", re.compile(r"\b(güvenlik|kript|sızma|yetkilendirme|kimlik|kvkk|kişisel\s+veri)\b", re.I)),
    ("ADMINISTRATIVE", re.compile(r"\b(idari|teklif|geçici\s+teminat|yeterlik|iş\s+deneyim)\b", re.I)),
    ("FINANCIAL", re.compile(r"\b(fiyat|bedel|ödeme|mali|teminat|avans)\b", re.I)),
    ("OPERATIONAL", re.compile(r"\b(destek|bakım|operasyon|7/24|kesinti|süreklilik)\b", re.I)),
    ("LEGAL", re.compile(r"\b(sözleşme|hukuk|cezai|fesih|uyuşmazlık|kanun)\b", re.I)),
]


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


def _category(text: str) -> str:
    for name, pattern in CATEGORY_RULES:
        if pattern.search(text or ""):
            return name
    return "OTHER"


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
                "category": _category(body),
                "obligationLevel": obligation,
                "pageStart": clause.get("pageStart"),
                "pageEnd": clause.get("pageEnd"),
                "contentHash": _hash(req_text),
                "metadata": {
                    "extractor": "requirement_from_clauses",
                    "clauseNumber": clause.get("clauseNumber"),
                },
            }
        )
    return requirements


def attach_requirements_to_result(result: dict[str, Any]) -> dict[str, Any]:
    """Attach requirements list into an existing parse result (non-destructive)."""
    enriched = dict(result)
    clauses = list(enriched.get("clauses") or [])
    requirements = requirements_from_clauses(clauses)
    enriched["requirements"] = requirements
    metadata = dict(enriched.get("metadata") or {})
    metadata["requirementCount"] = len(requirements)
    metadata["requirementExtractor"] = "requirement_from_clauses"
    enriched["metadata"] = metadata
    return enriched
