"""
Extract CompanyCapability records from company document text.

Deterministic-first (certificates, authorizations, numeric product specs).
Does not invent expiry dates — missing → validityStatus=UNKNOWN.
"""

from __future__ import annotations

import re
import uuid
from typing import Iterable

from company_fit.domain.models import CapabilityKind, CompanyCapability, CompanyDocument


def _id() -> str:
    return str(uuid.uuid4())


# ---------------------------------------------------------------------------
# Patterns
# ---------------------------------------------------------------------------

_ISO = re.compile(
    r"(?i)\b(ISO\s*/?\s*IEC\s*)?(27001|27002|9001|14001|45001)\b"
)
_TSE_CE = re.compile(r"(?i)\b(TSE|TSEK|CE\b|Ürün\s*Sertifika)\b")
_BRAND = re.compile(
    r"(?i)\b(yetkili\s*(bayi|iş\s*ortağ[iı]|partner|distributor|dağıtıcı)|"
    r"authorized\s*(partner|dealer)|vmware\s*(partner|authorized)|"
    r"dell\s*partner|hpe\s*partner|cisco\s*partner)\b"
)
_EXPIRY = re.compile(
    r"(?i)(?:geçerlilik|expiry|valid\s*(?:until|to)|son\s*geçerlilik|bit[iı][sş]\s*tarih[iı])"
    r"\s*[:\-]?\s*(\d{1,2}[./]\d{1,2}[./]\d{2,4}|\d{4}-\d{2}-\d{2})"
)
_ISSUE = re.compile(
    r"(?i)(?:düzenleme|issue\s*date|verili[sş]\s*tarih[iı])"
    r"\s*[:\-]?\s*(\d{1,2}[./]\d{1,2}[./]\d{2,4}|\d{4}-\d{2}-\d{2})"
)
_DIMM = re.compile(r"(?i)\b(?:en\s*az\s*)?(\d+)\s*(?:adet\s*)?(DIMM|bellek\s*yuvas)")
_CPU = re.compile(
    r"(?i)\b(?:en\s*az\s*)?(\d+)\s*(?:adet\s*)?(?:işlemci|cpu|xeon|çekirdek|core)"
)
_PERSONNEL_YEARS = re.compile(
    r"(?i)\b(\d+)\s*y[iı]l\s*(?:deneyim|tecrübe)|deneyim[:\s]+(\d+)\s*y[iı]l"
)
_FINANCIAL = re.compile(
    r"(?i)\b(teminat\s*mektubu|banka\s*referans|ciro|mali\s*yeterlik|bilanço)\b"
)
_ADMIN = re.compile(
    r"(?i)\b(ticaret\s*sicil|oda\s*kayıt|vergi\s*levhas[iı]|sgk|faaliyet\s*belgesi)\b"
)


def extract_capabilities_from_text(
    organization_id: str,
    doc: CompanyDocument,
    text: str,
) -> list[CompanyCapability]:
    caps: list[CompanyCapability] = []
    if not text or not text.strip():
        return caps

    # Certificates
    for m in _ISO.finditer(text):
        std = m.group(2)
        key = f"iso_{std}"
        valid_to, status = _validity(text)
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.COMPLIANCE_CERT,
                canonicalKey=key,
                label=f"ISO {std}",
                attributes={"standard": f"ISO {std}"},
                validTo=valid_to,
                validityStatus=status,
                sourceDocumentId=doc.documentId,
                evidenceSnippet=text[max(0, m.start() - 20) : m.end() + 40][:160],
                confidence=0.9,
            )
        )

    if _TSE_CE.search(text):
        valid_to, status = _validity(text)
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.COMPLIANCE_CERT,
                canonicalKey="tse_or_ce",
                label="TSE/CE belgesi",
                attributes={},
                validTo=valid_to,
                validityStatus=status,
                sourceDocumentId=doc.documentId,
                evidenceSnippet="TSE/CE signal",
                confidence=0.8,
            )
        )

    if _BRAND.search(text):
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.BRAND_AUTH,
                canonicalKey="brand_authorization",
                label="Üretici / partner yetkisi",
                attributes={},
                sourceDocumentId=doc.documentId,
                evidenceSnippet=_snip(text, _BRAND),
                confidence=0.85,
            )
        )

    for m in _DIMM.finditer(text):
        n = int(m.group(1))
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.PRODUCT_SPEC,
                canonicalKey="min_dimm_slots",
                label=f"DIMM slot ≥ {n}",
                attributes={"measurement": "dimm", "operator": ">=", "value": n},
                sourceDocumentId=doc.documentId,
                evidenceSnippet=m.group(0),
                confidence=0.88,
            )
        )

    for m in _CPU.finditer(text):
        n = int(m.group(1))
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.PRODUCT_SPEC,
                canonicalKey="min_cpu_or_cores",
                label=f"CPU/core ≥ {n}",
                attributes={"measurement": "cpu_or_core", "operator": ">=", "value": n},
                sourceDocumentId=doc.documentId,
                evidenceSnippet=m.group(0),
                confidence=0.82,
            )
        )

    m = _PERSONNEL_YEARS.search(text)
    if m:
        years = int(m.group(1) or m.group(2))
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.PERSONNEL,
                canonicalKey="personnel_years",
                label=f"Personel deneyim ≥ {years} yıl",
                attributes={"years": years},
                sourceDocumentId=doc.documentId,
                evidenceSnippet=m.group(0),
                confidence=0.8,
            )
        )

    if _FINANCIAL.search(text):
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.FINANCIAL,
                canonicalKey="financial_document",
                label="Mali belge / teminat kapasitesi sinyali",
                sourceDocumentId=doc.documentId,
                evidenceSnippet=_snip(text, _FINANCIAL),
                confidence=0.7,
            )
        )

    if _ADMIN.search(text):
        caps.append(
            CompanyCapability(
                capabilityId=_id(),
                organizationId=organization_id,
                kind=CapabilityKind.ADMINISTRATIVE,
                canonicalKey="administrative_registration",
                label="İdari kayıt belgesi",
                sourceDocumentId=doc.documentId,
                evidenceSnippet=_snip(text, _ADMIN),
                confidence=0.75,
            )
        )

    return _dedupe(caps)


def extract_from_documents(
    organization_id: str,
    docs: Iterable[tuple[CompanyDocument, str]],
) -> list[CompanyCapability]:
    all_caps: list[CompanyCapability] = []
    for doc, text in docs:
        all_caps.extend(extract_capabilities_from_text(organization_id, doc, text))
    return _dedupe(all_caps)


def _validity(text: str) -> tuple[str | None, str]:
    m = _EXPIRY.search(text)
    if not m:
        return None, "UNKNOWN"
    return m.group(1), "VALID"  # date parse/compare left to application clock


def _snip(text: str, pat: re.Pattern[str]) -> str:
    m = pat.search(text)
    if not m:
        return ""
    return text[max(0, m.start() - 10) : m.end() + 30][:160]


def _dedupe(caps: list[CompanyCapability]) -> list[CompanyCapability]:
    seen: set[tuple[str, str]] = set()
    out: list[CompanyCapability] = []
    for c in caps:
        key = (c.canonicalKey, str(c.attributes.get("value", "")))
        if key in seen:
            continue
        seen.add(key)
        out.append(c)
    return out
