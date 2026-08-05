"""
Match tender requirements against company capabilities.

Policy company-fit-v1:
- Only MUST (or obligationLevel=MUST) rows drive overall FIT / NOT_FIT
- TECHNICAL/COMPLIANCE/SECURITY/DOCUMENT weighted higher
- Evidence required for MET
- Never invent capabilities
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from domain.models import (
    CompanyCapability,
    CompanyFitReport,
    FitEvidence,
    FitStatus,
    OverallFit,
    RequirementFit,
)


MUST = {"MUST", "REQUIRED", "MANDATORY", "ZORUNLU"}

# requirement category / text cues → capability canonical keys
CATEGORY_KEYS: dict[str, list[str]] = {
    "COMPLIANCE": ["iso_27001", "iso_9001", "tse_or_ce", "iso_14001", "iso_45001"],
    "SECURITY": ["iso_27001", "security_ctrl"],
    "DOCUMENT": ["administrative_registration", "financial_document", "tse_or_ce"],
    "PERSONNEL": ["personnel_years"],
    "FINANCIAL": ["financial_document"],
    "TECHNICAL": ["min_dimm_slots", "min_cpu_or_cores", "brand_authorization", "product_spec"],
    "OPERATIONAL": ["brand_authorization", "personnel_years"],
}

TEXT_KEY_RULES: list[tuple[str, str]] = [
    (r"(?i)iso\s*27001", "iso_27001"),
    (r"(?i)iso\s*9001", "iso_9001"),
    (r"(?i)\b(tse|ce)\b", "tse_or_ce"),
    (r"(?i)vmware|yetkili|partner", "brand_authorization"),
    (r"(?i)dimm|bellek\s*yuvas", "min_dimm_slots"),
    (r"(?i)çekirdek|işlemci|xeon|cpu", "min_cpu_or_cores"),
    (r"(?i)deneyim|personel|uzman", "personnel_years"),
    (r"(?i)teminat|mali\s*yeter", "financial_document"),
    (r"(?i)ticaret\s*sicil|vergi\s*levha|oda", "administrative_registration"),
]


def _is_must(req: dict[str, Any]) -> bool:
    p = str(req.get("priority") or req.get("obligationLevel") or "").upper()
    return p in MUST


def _wanted_keys(req: dict[str, Any]) -> list[str]:
    import re

    keys: list[str] = []
    cat = str(req.get("category") or "").upper()
    keys.extend(CATEGORY_KEYS.get(cat, []))
    text = f"{req.get('title') or ''}\n{req.get('text') or ''}"
    for pat, key in TEXT_KEY_RULES:
        if re.search(pat, text):
            keys.append(key)
    # measurable product thresholds
    if req.get("measurement") and req.get("acceptanceThreshold"):
        m = str(req.get("measurement") or "").lower()
        if "dimm" in m:
            keys.append("min_dimm_slots")
        if any(x in m for x in ("çekirdek", "core", "cpu", "işlemci")):
            keys.append("min_cpu_or_cores")
    # unique preserve order
    seen: set[str] = set()
    out: list[str] = []
    for k in keys:
        if k not in seen:
            seen.add(k)
            out.append(k)
    return out


def _cap_by_key(
    caps: list[CompanyCapability],
) -> dict[str, list[CompanyCapability]]:
    idx: dict[str, list[CompanyCapability]] = {}
    for c in caps:
        idx.setdefault(c.canonicalKey, []).append(c)
    return idx


def _numeric_met(req: dict[str, Any], cap: CompanyCapability) -> bool | None:
    """Return True/False if both sides numeric; None if not comparable."""
    try:
        need = float(str(req.get("acceptanceThreshold")).replace(",", "."))
    except (TypeError, ValueError):
        return None
    val = cap.attributes.get("value")
    if val is None:
        return None
    try:
        have = float(val)
    except (TypeError, ValueError):
        return None
    op = str(req.get("operator") or ">=")
    if op in (">=", "=>"):
        return have >= need
    if op == "<=":
        return have <= need
    if op == "==":
        return have == need
    return have >= need


def match_requirement(
    req: dict[str, Any],
    caps: list[CompanyCapability],
) -> RequirementFit:
    rid = str(req.get("requirementId") or req.get("id") or "")
    preview = (req.get("text") or req.get("title") or "")[:180]
    wanted = _wanted_keys(req)
    idx = _cap_by_key(caps)

    if not wanted:
        return RequirementFit(
            requirementId=rid,
            category=req.get("category"),
            priority=req.get("priority") or req.get("obligationLevel"),
            textPreview=preview,
            status=FitStatus.AMBIGUOUS,
            score=0.0,
            reasons=["no_capability_mapping"],
            suggestedAction="Manuel eşleştirme veya canonical key ekle",
        )

    evidence: list[FitEvidence] = []
    reasons: list[str] = []
    best = 0.0
    partial = False

    for key in wanted:
        for cap in idx.get(key, []):
            if cap.validityStatus == "EXPIRED":
                reasons.append(f"expired:{key}")
                partial = True
                continue
            num = _numeric_met(req, cap)
            if num is False:
                reasons.append(f"threshold_not_met:{key}")
                partial = True
                evidence.append(
                    FitEvidence(
                        capabilityId=cap.capabilityId,
                        documentId=cap.sourceDocumentId,
                        snippet=cap.evidenceSnippet,
                        note="threshold_not_met",
                    )
                )
                continue
            # met
            score = min(1.0, 0.6 + 0.4 * float(cap.confidence or 0))
            if num is True:
                score = min(1.0, score + 0.1)
            best = max(best, score)
            evidence.append(
                FitEvidence(
                    capabilityId=cap.capabilityId,
                    documentId=cap.sourceDocumentId,
                    snippet=cap.evidenceSnippet,
                )
            )
            reasons.append(f"matched:{key}")

    if best >= 0.6 and evidence:
        status = FitStatus.MET
        action = None
    elif partial or (best > 0 and best < 0.6):
        status = FitStatus.PARTIAL
        action = "Eksik eşik / güncel belge yükle"
    else:
        status = FitStatus.MISSING
        action = f"Eksik yetenek: {', '.join(wanted[:3])}"
        reasons.append("no_matching_capability")

    return RequirementFit(
        requirementId=rid,
        category=req.get("category"),
        priority=req.get("priority") or req.get("obligationLevel"),
        textPreview=preview,
        status=status,
        score=best,
        reasons=reasons,
        evidence=evidence,
        suggestedAction=action,
    )


def build_fit_report(
    organization_id: str,
    tender_document_id: str,
    requirements: list[dict[str, Any]],
    capabilities: list[CompanyCapability],
    *,
    must_coverage_fit: float = 0.85,
    must_coverage_conditional: float = 0.55,
) -> CompanyFitReport:
    rows = [match_requirement(r, capabilities) for r in requirements]

    must_rows = [r for r, req in zip(rows, requirements) if _is_must(req)]
    if not must_rows:
        # fall back to all rows if priority not tagged
        must_rows = rows

    must_total = len(must_rows)
    must_met = sum(1 for r in must_rows if r.status == FitStatus.MET)
    coverage = (must_met / must_total) if must_total else 0.0

    missing_critical = [
        r.requirementId
        for r in must_rows
        if r.status in (FitStatus.MISSING, FitStatus.AMBIGUOUS)
        and str(r.category or "").upper()
        in {"TECHNICAL", "COMPLIANCE", "SECURITY", "DOCUMENT", "FINANCIAL"}
    ]

    if not capabilities:
        overall = OverallFit.INSUFFICIENT_DATA
    elif coverage >= must_coverage_fit and not missing_critical:
        overall = OverallFit.FIT
    elif coverage >= must_coverage_conditional:
        overall = OverallFit.CONDITIONAL
    else:
        overall = OverallFit.NOT_FIT

    return CompanyFitReport(
        organizationId=organization_id,
        tenderDocumentId=tender_document_id,
        overall=overall,
        overallScore=round(coverage, 4),
        mustMet=must_met,
        mustTotal=must_total,
        missingCritical=missing_critical,
        rows=rows,
        generatedAt=datetime.now(timezone.utc).isoformat(),
    )
