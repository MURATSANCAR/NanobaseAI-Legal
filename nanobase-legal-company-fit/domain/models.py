"""Domain models for company capability fit."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime
from enum import Enum
from typing import Any
from uuid import UUID, uuid4


class CapabilityType(str, Enum):
    CERTIFICATION = "CERTIFICATION"
    LICENSE = "LICENSE"
    AUTHORIZATION = "AUTHORIZATION"
    QUALIFIED_PERSONNEL = "QUALIFIED_PERSONNEL"
    PERSONNEL_COUNT = "PERSONNEL_COUNT"
    FINANCIAL_METRIC = "FINANCIAL_METRIC"
    TURNOVER = "TURNOVER"
    BANK_GUARANTEE_CAPACITY = "BANK_GUARANTEE_CAPACITY"
    TECHNOLOGY = "TECHNOLOGY"
    INFRASTRUCTURE = "INFRASTRUCTURE"
    SECURITY_CONTROL = "SECURITY_CONTROL"
    PROJECT_EXPERIENCE = "PROJECT_EXPERIENCE"
    LEGAL_DOCUMENT = "LEGAL_DOCUMENT"
    OTHER = "OTHER"


class ValueType(str, Enum):
    TEXT = "TEXT"
    NUMERIC = "NUMERIC"
    BOOLEAN = "BOOLEAN"
    DATE = "DATE"
    STRUCTURED = "STRUCTURED"


class VerificationStatus(str, Enum):
    UNVERIFIED = "UNVERIFIED"
    AI_EXTRACTED = "AI_EXTRACTED"
    USER_CONFIRMED = "USER_CONFIRMED"
    EXPERT_CONFIRMED = "EXPERT_CONFIRMED"
    EXPIRED = "EXPIRED"
    REVOKED = "REVOKED"


class RowStatus(str, Enum):
    """Per-requirement fit row (product-facing labels)."""

    MET = "MET"
    PARTIAL = "PARTIAL"
    MISSING = "MISSING"
    UNKNOWN = "UNKNOWN"
    EXPIRED = "EXPIRED"
    OUT_OF_SCOPE = "OUT_OF_SCOPE"

    @classmethod
    def from_matcher(cls, status: str) -> RowStatus:
        """Map Java RequirementCapabilityMatcher.MatchStatus → product row."""
        return {
            "MATCHED": cls.MET,
            "PARTIALLY_MATCHED": cls.PARTIAL,
            "NOT_MATCHED": cls.MISSING,
            "UNKNOWN": cls.UNKNOWN,
            "EXPIRED": cls.EXPIRED,
            "OUT_OF_SCOPE": cls.OUT_OF_SCOPE,
        }.get((status or "").upper(), cls.UNKNOWN)


class OverallFit(str, Enum):
    FIT = "FIT"
    CONDITIONAL = "CONDITIONAL"
    NOT_FIT = "NOT_FIT"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


@dataclass
class CompanyDocument:
    organization_id: UUID
    document_id: UUID | None
    text: str
    title: str = ""
    source_kind: str = "COMPANY_EVIDENCE"  # sertifika, yetki, mali, personel…


@dataclass
class Capability:
    organization_id: UUID
    capability_type: CapabilityType
    name: str
    normalized_name: str
    value_type: ValueType = ValueType.TEXT
    text_value: str | None = None
    numeric_value: float | None = None
    unit: str | None = None
    boolean_value: bool | None = None
    date_value: date | None = None
    valid_until: date | None = None
    scope_text: str | None = None
    verification_status: VerificationStatus = VerificationStatus.AI_EXTRACTED
    source_document_id: UUID | None = None
    confidence: float = 0.7
    id: UUID = field(default_factory=uuid4)
    evidence_snippet: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_snapshot_dict(self) -> dict[str, Any]:
        return {
            "id": str(self.id),
            "capabilityType": self.capability_type.value,
            "normalizedName": self.normalized_name,
            "textValue": self.text_value or self.name,
            "numericValue": self.numeric_value,
            "unit": self.unit,
            "booleanValue": self.boolean_value,
            "dateValue": self.date_value.isoformat() if self.date_value else None,
            "validUntil": self.valid_until.isoformat() if self.valid_until else None,
            "scope": self.scope_text,
            "status": "ACTIVE",
            "verificationStatus": self.verification_status.value,
            "confidence": self.confidence,
            "sourceDocumentId": str(self.source_document_id)
            if self.source_document_id
            else None,
        }


@dataclass
class FitRequirement:
    """Minimal requirement view for fit (from DI categorizer / DB)."""

    requirement_id: UUID
    text: str
    title: str = ""
    category: str = "OTHER"
    obligation: str = "MUST"
    field_name: str | None = None
    expected_value: str | None = None
    expected_numeric: float | None = None
    unit: str | None = None
    mandatory: bool = True


@dataclass
class FitRow:
    requirement_id: UUID
    requirement_text: str
    category: str
    status: RowStatus
    matched_capability_ids: list[UUID] = field(default_factory=list)
    rationale: str = ""
    confidence: float = 0.0


@dataclass
class FitReport:
    organization_id: UUID
    document_id: UUID  # şartname document
    overall: OverallFit
    rows: list[FitRow]
    met: int = 0
    partial: int = 0
    missing: int = 0
    unknown: int = 0
    capability_count: int = 0
    generated_at: datetime = field(default_factory=datetime.utcnow)
    id: UUID = field(default_factory=uuid4)

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": str(self.id),
            "organizationId": str(self.organization_id),
            "documentId": str(self.document_id),
            "overall": self.overall.value,
            "summary": {
                "met": self.met,
                "partial": self.partial,
                "missing": self.missing,
                "unknown": self.unknown,
                "capabilityCount": self.capability_count,
                "requirementCount": len(self.rows),
            },
            "rows": [
                {
                    "requirementId": str(r.requirement_id),
                    "requirementText": r.requirement_text[:500],
                    "category": r.category,
                    "status": r.status.value,
                    "matchedCapabilityIds": [str(x) for x in r.matched_capability_ids],
                    "rationale": r.rationale,
                    "confidence": r.confidence,
                }
                for r in self.rows
            ],
            "generatedAt": self.generated_at.isoformat() + "Z",
        }
