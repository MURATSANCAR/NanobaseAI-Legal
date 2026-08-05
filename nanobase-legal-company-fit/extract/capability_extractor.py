"""Deterministic capability extraction from company document text."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable

from ..domain.models import (
    Capability,
    CapabilityType,
    CompanyDocument,
    ValueType,
    VerificationStatus,
)


@dataclass(frozen=True)
class _Rule:
    pattern: re.Pattern[str]
    capability_type: CapabilityType
    name: str
    value_type: ValueType = ValueType.TEXT
    confidence: float = 0.75


_FLAGS = re.IGNORECASE | re.UNICODE


def _re(p: str) -> re.Pattern[str]:
    return re.compile(p, _FLAGS)


_RULES: list[_Rule] = [
    _Rule(_re(r"\biso\s*27001\b"), CapabilityType.CERTIFICATION, "ISO 27001"),
    _Rule(_re(r"\biso\s*27002\b"), CapabilityType.CERTIFICATION, "ISO 27002"),
    _Rule(_re(r"\biso\s*9001\b"), CapabilityType.CERTIFICATION, "ISO 9001"),
    _Rule(_re(r"\biso\s*14001\b"), CapabilityType.CERTIFICATION, "ISO 14001"),
    _Rule(_re(r"\biso\s*45001\b"), CapabilityType.CERTIFICATION, "ISO 45001"),
    _Rule(_re(r"\bbgys\b"), CapabilityType.CERTIFICATION, "BGYS"),
    _Rule(_re(r"\btse\b"), CapabilityType.CERTIFICATION, "TSE"),
    _Rule(
        _re(r"\bce\s*belgesi\b|\bce\s*işareti\b|\bce\s*mark\b"),
        CapabilityType.CERTIFICATION,
        "CE",
    ),
    _Rule(_re(r"\bul\s*(?:listed|belge|sertifika)?\b"), CapabilityType.CERTIFICATION, "UL"),
    _Rule(_re(r"\biec\b"), CapabilityType.CERTIFICATION, "IEC"),
    _Rule(
        _re(r"üretici\s+yetki|yetkili\s+satıcı|distribütör\s+belgesi|bayi\s+belgesi"),
        CapabilityType.AUTHORIZATION,
        "Authorized reseller",
    ),
    _Rule(
        _re(r"ticaret\s+sicil|imza\s+sirkü"),
        CapabilityType.LEGAL_DOCUMENT,
        "Trade registry",
    ),
    _Rule(
        _re(r"sgk\s+borcu\s+yoktur|vadesi\s+geçmiş\s+borcu\s+yoktur"),
        CapabilityType.LEGAL_DOCUMENT,
        "Debt clearance",
    ),
    _Rule(_re(r"\bkvkk\b"), CapabilityType.SECURITY_CONTROL, "KVKK compliance"),
    _Rule(_re(r"\bgdpr\b"), CapabilityType.SECURITY_CONTROL, "GDPR compliance"),
    _Rule(_re(r"\bmfa\b|çok\s+faktörlü|2fa"), CapabilityType.SECURITY_CONTROL, "MFA"),
    _Rule(
        _re(r"\bwaf\b|firewall|güvenlik\s+duvarı"),
        CapabilityType.SECURITY_CONTROL,
        "Perimeter security",
    ),
    _Rule(_re(r"\bdimm\b"), CapabilityType.TECHNOLOGY, "DIMM capacity", ValueType.TEXT, 0.65),
    _Rule(_re(r"\bnvme\b"), CapabilityType.TECHNOLOGY, "NVMe storage", ValueType.TEXT, 0.65),
    _Rule(
        _re(r"\bxeon\b|\bepyc\b"),
        CapabilityType.TECHNOLOGY,
        "Server CPU platform",
        ValueType.TEXT,
        0.65,
    ),
    _Rule(
        _re(r"data\s*center|veri\s*merkezi"),
        CapabilityType.INFRASTRUCTURE,
        "Data center",
    ),
    _Rule(
        _re(r"sertifikalı\s+personel|certified\s+(?:staff|personnel)"),
        CapabilityType.QUALIFIED_PERSONNEL,
        "Certified personnel",
    ),
    _Rule(
        _re(r"en\s+az\s+(\d+)\s+(?:yıl|sene|year)"),
        CapabilityType.QUALIFIED_PERSONNEL,
        "Years of experience",
        ValueType.NUMERIC,
        0.7,
    ),
    _Rule(
        _re(r"(\d+)\s+(?:kişilik|personel|çalışan|employee)"),
        CapabilityType.PERSONNEL_COUNT,
        "Headcount",
        ValueType.NUMERIC,
        0.7,
    ),
    _Rule(
        _re(r"teminat\s+mektubu|banka\s+teminat|bid\s+bond|performance\s+bond"),
        CapabilityType.BANK_GUARANTEE_CAPACITY,
        "Bank guarantee capacity",
    ),
    _Rule(_re(r"ciro|turnover|yıllık\s+gelir"), CapabilityType.TURNOVER, "Turnover"),
    _Rule(
        _re(r"mali\s+yeterlik|bilanço|balance\s+sheet"),
        CapabilityType.FINANCIAL_METRIC,
        "Financial standing",
    ),
]

_NUM = re.compile(r"(\d+(?:[.,]\d+)?)", _FLAGS)


def _normalize(name: str) -> str:
    return " ".join(name.lower().split())


def _snippet(text: str, start: int, end: int, pad: int = 60) -> str:
    a = max(0, start - pad)
    b = min(len(text), end + pad)
    return " ".join(text[a:b].split())


def extract_capabilities(doc: CompanyDocument) -> list[Capability]:
    text = doc.text or ""
    if not text.strip():
        return []
    found: dict[str, Capability] = {}
    for rule in _RULES:
        for m in rule.pattern.finditer(text):
            key = f"{rule.capability_type.value}:{_normalize(rule.name)}"
            numeric = None
            unit = None
            if rule.value_type == ValueType.NUMERIC:
                nm = _NUM.search(m.group(0))
                if nm:
                    numeric = float(nm.group(1).replace(",", "."))
                    low = m.group(0).lower()
                    if any(x in low for x in ("yıl", "year", "sene")):
                        unit = "year"
                    elif any(x in low for x in ("kişi", "personel", "employee", "çalışan")):
                        unit = "person"
            cap = Capability(
                organization_id=doc.organization_id,
                capability_type=rule.capability_type,
                name=rule.name,
                normalized_name=_normalize(rule.name),
                value_type=rule.value_type,
                text_value=rule.name,
                numeric_value=numeric,
                unit=unit,
                verification_status=VerificationStatus.AI_EXTRACTED,
                source_document_id=doc.document_id,
                confidence=rule.confidence,
                evidence_snippet=_snippet(text, m.start(), m.end()),
                metadata={"sourceKind": doc.source_kind, "title": doc.title},
            )
            prev = found.get(key)
            if prev is None or (cap.confidence >= prev.confidence and cap.evidence_snippet):
                found[key] = cap
    return list(found.values())


def extract_many(docs: Iterable[CompanyDocument]) -> list[Capability]:
    by_key: dict[str, Capability] = {}
    for doc in docs:
        for cap in extract_capabilities(doc):
            key = f"{cap.capability_type.value}:{cap.normalized_name}"
            prev = by_key.get(key)
            if prev is None or cap.confidence > prev.confidence:
                by_key[key] = cap
    return list(by_key.values())
