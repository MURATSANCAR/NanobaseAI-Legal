"""
Company capability & tender-fit domain models.

Aligns with existing Nanobase capability concept (owner_entity + evidence)
but adds a first-class company profile used for tender fit.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any


class CapabilityKind(str, Enum):
    COMPLIANCE_CERT = "COMPLIANCE_CERT"  # ISO, TSE, CE…
    BRAND_AUTH = "BRAND_AUTH"  # üretici yetki
    PERSONNEL = "PERSONNEL"
    EXPERIENCE = "EXPERIENCE"  # iş bitirme / referans
    PRODUCT_SPEC = "PRODUCT_SPEC"  # katalog / datasheet spec
    FINANCIAL = "FINANCIAL"
    ADMINISTRATIVE = "ADMINISTRATIVE"  # oda, vergi, SGK
    SECURITY_CTRL = "SECURITY_CTRL"
    OTHER = "OTHER"


class FitStatus(str, Enum):
    MET = "MET"
    PARTIAL = "PARTIAL"
    MISSING = "MISSING"
    AMBIGUOUS = "AMBIGUOUS"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class OverallFit(str, Enum):
    FIT = "FIT"  # teklif verilebilir
    CONDITIONAL = "CONDITIONAL"  # eksikler kapanırsa
    NOT_FIT = "NOT_FIT"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


@dataclass
class CompanyDocument:
    documentId: str
    organizationId: str
    docType: str  # CERTIFICATE | AUTHORIZATION | REFERENCE | CATALOG | FINANCIAL | OTHER
    title: str | None = None
    storageKey: str | None = None
    pageCount: int | None = None
    extractedTextLen: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class CompanyCapability:
    capabilityId: str
    organizationId: str
    kind: CapabilityKind
    canonicalKey: str  # e.g. iso_27001, vmware_partner, min_dimm_slots
    label: str
    attributes: dict[str, Any] = field(default_factory=dict)
    # validity
    validFrom: str | None = None  # ISO date
    validTo: str | None = None
    validityStatus: str = "UNKNOWN"  # VALID | EXPIRED | UNKNOWN
    # evidence
    sourceDocumentId: str | None = None
    evidenceSnippet: str | None = None
    confidence: float = 0.0

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["kind"] = self.kind.value
        return d


@dataclass
class FitEvidence:
    capabilityId: str | None
    documentId: str | None
    snippet: str | None
    note: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class RequirementFit:
    requirementId: str
    category: str | None
    priority: str | None
    textPreview: str
    status: FitStatus
    score: float  # 0..1
    reasons: list[str] = field(default_factory=list)
    evidence: list[FitEvidence] = field(default_factory=list)
    suggestedAction: str | None = None

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["status"] = self.status.value
        d["evidence"] = [e if isinstance(e, dict) else e.to_dict() for e in self.evidence]
        return d


@dataclass
class CompanyFitReport:
    organizationId: str
    tenderDocumentId: str
    overall: OverallFit
    overallScore: float
    mustMet: int
    mustTotal: int
    missingCritical: list[str]
    rows: list[RequirementFit]
    generatedAt: str | None = None
    policyVersion: str = "company-fit-v1"

    def to_dict(self) -> dict[str, Any]:
        return {
            "organizationId": self.organizationId,
            "tenderDocumentId": self.tenderDocumentId,
            "overall": self.overall.value,
            "overallScore": self.overallScore,
            "mustMet": self.mustMet,
            "mustTotal": self.mustTotal,
            "missingCritical": self.missingCritical,
            "rows": [r.to_dict() for r in self.rows],
            "generatedAt": self.generatedAt,
            "policyVersion": self.policyVersion,
        }
