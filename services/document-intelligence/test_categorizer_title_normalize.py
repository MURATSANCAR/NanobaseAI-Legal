"""Tests for title spacing normalize + requirement categorizer v2."""

from __future__ import annotations

from requirement_categorizer_v2 import categorize_many, categorize_requirement
from requirement_from_clauses import requirements_from_clauses
from title_spacing_normalize import normalize_clause_titles, normalize_title


def test_title_spacing_idempotent_with_parens():
    once = normalize_title("3.1. SU NU CU Tİ P 1 ( 1 5 AD ET)")
    assert once == "3.1. SUNUCU TİP 1 (15 ADET)"
    assert normalize_title(once) == once


def test_categorizer_yuklenici_sorumlu_not_iso():
    assert (
        categorize_requirement(
            "Montaj çalışmaları esnasında gerekli iş güvenliği YÜKLENİCİ tarafından sağlanacaktır."
        )
        == "PERSONNEL"
    )


def test_title_spacing_isin_konusu():
    assert normalize_title("1. İŞ İN KO NU SU") == "1. İŞİN KONUSU"


def test_normalize_clause_titles_list():
    clauses = [
        {
            "sourceId": "a",
            "title": "1. İŞ İN KO NU SU",
            "rawText": "1. İŞ İN KO NU SU\nMetin.",
        }
    ]
    out = normalize_clause_titles(clauses)
    assert out[0]["title"] == "1. İŞİN KONUSU"
    assert out[0]["metadata"]["titleSpacingNormalized"] is True


def test_categorizer_technical_xeon_dimm():
    assert (
        categorize_requirement("Teklif edilecek sunucuda en az 2 adet Intel Xeon Gold ve en az 16 DIMM bulunacaktır.")
        == "TECHNICAL"
    )


def test_categorizer_compliance_before_other():
    assert categorize_requirement("Ürün TSE ve ISO 9001 sertifikasına uygun olacaktır.") == "COMPLIANCE"


def test_categorizer_gizlilik_maps_to_security():
    assert (
        categorize_requirement(
            "4.3 Ancak, bu bilgiyi alan Tarafın sorumluluğu gerektirmeden söz konusu bilginin "
            "zaten biliniyor olması veya bilginin bu bilgiyi alan tarafından gizlilik kuralının "
            "ihlali olmaksızın kamuya açık hale gelmesi halinde yukarıdaki şartlar geçerli olmayacaktır."
        )
        == "SECURITY"
    )
    assert (
        categorize_requirement(
            "4.3 Gizlilik: Taraflar edindikleri bilgileri gizli tutacaktır."
        )
        == "SECURITY"
    )


def test_categorizer_document_not_compliance_ce_false_positive():
    assert categorize_requirement("katalog ve datasheet belgeleri teslim edilecektir") == "DOCUMENT"


def test_categorizer_many_and_extractor_wire():
    clauses = [
        {
            "sourceId": "md-1",
            "title": "3.1. SUNUCU TİP 1",
            "rawText": "Sunucuda en az 16 DIMM slot bulunacaktır. Yazılım lisansları teslim edilecektir.",
            "pageStart": 3,
            "pageEnd": 3,
            "clauseNumber": "3.1",
        }
    ]
    reqs = requirements_from_clauses(clauses)
    assert reqs
    assert reqs[0]["category"] == "TECHNICAL"
    assert reqs[0]["metadata"]["categorizer"] == "requirement_categorizer_v2"
    tagged = categorize_many([{"text": "KVKK ve 5651 uyumu zorunludur.", "title": "Güvenlik"}])
    assert tagged[0]["category"] == "SECURITY"
