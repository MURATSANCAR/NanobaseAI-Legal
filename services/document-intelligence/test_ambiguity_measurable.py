"""Measurable-field ambiguity auto-resolution tests."""

from __future__ import annotations

from textambiguity import (
    apply_auto_resolution,
    extract_measurable_fields,
    prioritize_ambiguities,
)


def test_quantity_dimm_extraction():
    fields = extract_measurable_fields("Yüklenici en az 16 adet DIMM bellek sağlayacaktır.")
    assert fields.operator == ">="
    assert fields.threshold == "16"
    assert "DIMM" in (fields.measurement or "").upper() or "adet" in (fields.measurement or "")
    assert fields.confidence >= 0.85
    assert fields.fields_complete()


def test_within_calendar_days():
    fields = extract_measurable_fields(
        "Arıza bildirimi sonrası 30 takvim günü içinde müdahale edilecektir."
    )
    assert fields.operator == "<="
    assert fields.threshold == "30"
    assert "takvim" in (fields.measurement or "") or fields.measurement == "gün"
    assert fields.confidence >= 0.85


def test_qualitative_presence_low_confidence():
    fields = extract_measurable_fields("Teklif edilen ürünler IPv6 için uygun olacaktır.")
    assert fields.operator == "=="
    assert fields.threshold == "true"
    assert fields.measurement == "presence"
    assert fields.confidence < 0.85


def test_auto_resolve_numeric_and_keep_qualitative():
    reqs = [
        {
            "requirementId": "r1",
            "text": "Sistemde en az 16 adet DIMM bulunacaktır.",
            "category": "TECHNICAL",
            "obligationLevel": "MUST",
            "attributes": {},
        },
        {
            "requirementId": "r2",
            "text": "Ürünler teknik şartnameye uygun olacaktır.",
            "category": "TECHNICAL",
            "obligationLevel": "MUST",
            "attributes": {},
        },
    ]
    updated, events = apply_auto_resolution(reqs)
    by_id = {r["requirementId"]: r for r in updated}
    assert by_id["r1"]["ambiguityStatus"] == "RESOLVED_STRUCTURED"
    assert by_id["r1"]["attributes"]["acceptanceThreshold"] == "16"
    assert by_id["r2"]["ambiguityStatus"] == "CANDIDATE"
    assert any(e["type"] == "AMBIGUITY_AUTO_RESOLVED" for e in events)

    queue = prioritize_ambiguities(updated)
    assert len(queue) == 1
    assert queue[0].requirement_id == "r2"
    assert queue[0].priority in {"HIGH", "MEDIUM"}
    assert queue[0].suggested_fields.get("measurement") == "presence"


def test_high_priority_tech_must_missing_threshold():
    reqs = [
        {
            "requirementId": "r3",
            "text": "Güvenlik duvarı kuralları tanımlanacaktır.",
            "category": "SECURITY",
            "obligationLevel": "MUST",
            "attributes": {
                "measurement": "firewall",
                "operator": ">=",
                "testCondition": "policy",
                # acceptanceThreshold intentionally missing
            },
            "ambiguityStatus": "CANDIDATE",
        }
    ]
    queue = prioritize_ambiguities(reqs)
    assert len(queue) == 1
    assert queue[0].priority == "HIGH"
    assert "missingAcceptanceThreshold" in queue[0].missing_fields
