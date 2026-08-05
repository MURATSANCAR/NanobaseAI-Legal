"""
Application service: company documents → capabilities → tender fit report.

Wire into Java backend via REST or internal Python worker callback.
"""

from __future__ import annotations

from typing import Any, Iterable

from company_fit.domain.models import CompanyDocument, CompanyFitReport
from company_fit.extract.capability_extractor import extract_from_documents
from company_fit.fit.fit_engine import build_fit_report


class CompanyFitService:
    def __init__(self, capability_store: Any | None = None, fit_store: Any | None = None):
        """
        capability_store / fit_store: optional ports with
          save_capabilities(org_id, caps)
          load_capabilities(org_id) -> list[CompanyCapability]
          save_fit_report(report)
        """
        self.capability_store = capability_store
        self.fit_store = fit_store

    def ingest_company_texts(
        self,
        organization_id: str,
        docs_with_text: Iterable[tuple[CompanyDocument, str]],
    ) -> list[dict[str, Any]]:
        caps = extract_from_documents(organization_id, docs_with_text)
        if self.capability_store is not None:
            self.capability_store.save_capabilities(organization_id, caps)
        return [c.to_dict() for c in caps]

    def evaluate_fit(
        self,
        organization_id: str,
        tender_document_id: str,
        requirements: list[dict[str, Any]],
        *,
        capabilities: list | None = None,
    ) -> dict[str, Any]:
        if capabilities is None:
            if self.capability_store is None:
                raise RuntimeError("capabilities required or capability_store must be set")
            capabilities = self.capability_store.load_capabilities(organization_id)
        report: CompanyFitReport = build_fit_report(
            organization_id,
            tender_document_id,
            requirements,
            capabilities,
        )
        if self.fit_store is not None:
            self.fit_store.save_fit_report(report)
        return report.to_dict()


# ---------------------------------------------------------------------------
# HTTP-shaped handlers (FastAPI-style signatures; adapt to DI/worker)
# ---------------------------------------------------------------------------

def handle_ingest(payload: dict[str, Any]) -> dict[str, Any]:
    """
    POST /v1/organizations/{orgId}/capabilities/ingest
    body: { documents: [ { documentId, docType, title, text } ] }
    """
    org = payload["organizationId"]
    pairs = []
    for d in payload.get("documents") or []:
        doc = CompanyDocument(
            documentId=d["documentId"],
            organizationId=org,
            docType=d.get("docType") or "OTHER",
            title=d.get("title"),
        )
        pairs.append((doc, d.get("text") or ""))
    svc = CompanyFitService()
    caps = svc.ingest_company_texts(org, pairs)
    return {"organizationId": org, "capabilityCount": len(caps), "capabilities": caps}


def handle_fit(payload: dict[str, Any]) -> dict[str, Any]:
    """
    POST /v1/tenders/{documentId}/company-fit
    body: {
      organizationId,
      requirements: [...],
      capabilities: [...]   # or loaded server-side
    }
    """
    from company_fit.domain.models import CapabilityKind, CompanyCapability

    org = payload["organizationId"]
    tender_id = payload["tenderDocumentId"]
    raw_caps = payload.get("capabilities") or []
    caps = []
    for c in raw_caps:
        caps.append(
            CompanyCapability(
                capabilityId=c.get("capabilityId") or c.get("id") or "cap",
                organizationId=org,
                kind=CapabilityKind(c.get("kind") or "OTHER"),
                canonicalKey=c["canonicalKey"],
                label=c.get("label") or c["canonicalKey"],
                attributes=c.get("attributes") or {},
                validTo=c.get("validTo"),
                validityStatus=c.get("validityStatus") or "UNKNOWN",
                sourceDocumentId=c.get("sourceDocumentId"),
                evidenceSnippet=c.get("evidenceSnippet"),
                confidence=float(c.get("confidence") or 0.8),
            )
        )
    svc = CompanyFitService()
    return svc.evaluate_fit(
        org,
        tender_id,
        payload.get("requirements") or [],
        capabilities=caps,
    )
