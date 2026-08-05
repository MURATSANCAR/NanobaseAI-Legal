"""Company capability extract + fit engine tests."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from domain.models import CompanyDocument, FitStatus, OverallFit
from extract.capability_extractor import extract_capabilities_from_text
from fit.fit_engine import build_fit_report, match_requirement
from api.fit_service import handle_ingest, handle_fit


def test_extract_iso_and_dimm():
    doc = CompanyDocument(
        documentId="d1",
        organizationId="org1",
        docType="CERTIFICATE",
        title="ISO Belgesi",
    )
    text = (
        "Şirketimiz ISO 27001 bilgi güvenliği belgesine sahiptir. "
        "Geçerlilik: 31.12.2027. Sunucu konfigürasyonunda en az 16 DIMM yuvası desteklenir."
    )
    caps = extract_capabilities_from_text("org1", doc, text)
    keys = {c.canonicalKey for c in caps}
    assert "iso_27001" in keys
    assert "min_dimm_slots" in keys


def test_fit_met_on_matching_capability():
    from domain.models import CapabilityKind, CompanyCapability

    caps = [
        CompanyCapability(
            capabilityId="c1",
            organizationId="org1",
            kind=CapabilityKind.PRODUCT_SPEC,
            canonicalKey="min_dimm_slots",
            label="DIMM 16",
            attributes={"value": 16},
            confidence=0.9,
            sourceDocumentId="d1",
            evidenceSnippet="16 DIMM",
        )
    ]
    req = {
        "requirementId": "r1",
        "category": "TECHNICAL",
        "priority": "MUST",
        "text": "Sunucu üzerinde en az 16 adet DIMM yuvası bulunmalıdır.",
        "measurement": "dimm",
        "operator": ">=",
        "acceptanceThreshold": "16",
    }
    row = match_requirement(req, caps)
    assert row.status == FitStatus.MET


def test_fit_missing_without_caps():
    req = {
        "requirementId": "r2",
        "category": "COMPLIANCE",
        "priority": "MUST",
        "text": "ISO 27001 belgesi sunulacaktır.",
    }
    row = match_requirement(req, [])
    assert row.status == FitStatus.MISSING


def test_overall_conditional():
    from domain.models import CapabilityKind, CompanyCapability

    caps = [
        CompanyCapability(
            capabilityId="c1",
            organizationId="org1",
            kind=CapabilityKind.COMPLIANCE_CERT,
            canonicalKey="iso_27001",
            label="ISO 27001",
            confidence=0.95,
            sourceDocumentId="d1",
        )
    ]
    reqs = [
        {
            "requirementId": "r1",
            "category": "COMPLIANCE",
            "priority": "MUST",
            "text": "ISO 27001 zorunludur.",
        },
        {
            "requirementId": "r2",
            "category": "TECHNICAL",
            "priority": "MUST",
            "text": "En az 16 DIMM yuvası bulunmalıdır.",
            "measurement": "dimm",
            "operator": ">=",
            "acceptanceThreshold": "16",
        },
    ]
    report = build_fit_report("org1", "tender1", reqs, caps)
    assert report.mustTotal == 2
    assert report.mustMet == 1
    assert report.overall in (OverallFit.CONDITIONAL, OverallFit.NOT_FIT)


def test_handle_ingest_and_fit_roundtrip():
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
