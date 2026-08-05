"""Smoke: company_fit module importable from document-intelligence."""

from company_fit.api.fit_service import handle_ingest, handle_fit


def test_company_fit_ingest_roundtrip():
    ingested = handle_ingest(
        {
            "organizationId": "org1",
            "documents": [
                {
                    "documentId": "d1",
                    "docType": "CERTIFICATE",
                    "text": "ISO 27001 sertifikası. Yetkili partner belgesi mevcut.",
                }
            ],
        }
    )
    assert ingested["capabilityCount"] >= 1
    fit = handle_fit(
        {
            "organizationId": "org1",
            "tenderDocumentId": "t1",
            "requirements": [
                {
                    "requirementId": "r1",
                    "category": "COMPLIANCE",
                    "priority": "MUST",
                    "text": "ISO 27001 belgesi istenmektedir.",
                }
            ],
            "capabilities": ingested["capabilities"],
        }
    )
    assert fit["mustMet"] >= 1
    assert fit["overall"] in ("FIT", "CONDITIONAL", "NOT_FIT", "INSUFFICIENT_DATA")
