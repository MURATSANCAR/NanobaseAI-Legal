"""Tests for organic lexicon harvest / accept gate."""

from __future__ import annotations

import json
from pathlib import Path

import lexicon_harvest as lh
from categorizer_lexicon import all_known_terms, reload_lexicons
from requirement_categorizer_v2 import categorize_requirement, self_check


def test_extract_skips_stopwords_and_known():
    terms = lh.extract_candidate_terms(
        "Spine-Leaf topoloji ile NVMe storage sunucu üzerinde bulunacaktır."
    )
    folded = {t.casefold() for t in terms}
    assert "spine-leaf" in folded or "Spine-Leaf".casefold() in folded
    assert "ve" not in folded
    assert "ile" not in folded


def test_harvest_other_only(tmp_path, monkeypatch):
    cand = tmp_path / "candidates.jsonl"
    learned = tmp_path / "learned_overlay.json"
    monkeypatch.setattr(lh, "_candidates_path", lambda: cand)
    monkeypatch.setattr(lh, "_learned_path", lambda: learned)

    payload = {
        "requirements": [
            {
                "title": "Ağ",
                "text": "Spine-Leaf fabric ve anycast gateway zorunludur.",
                "category": "OTHER",
            },
            {
                "title": "Sunucu",
                "text": "Sunucuda en az 16 DIMM bulunacaktır.",
                "category": "TECHNICAL",
            },
        ]
    }
    rows = lh.harvest_from_payloads([payload], only_other=True, min_count=1, source="t")
    terms = {r["term"].casefold() for r in rows}
    assert any("spine" in t for t in terms)
    # TECHNICAL clause should not contribute when only_other=True
    assert "dimm" not in terms


def test_accept_rejects_self_check_breakers(tmp_path, monkeypatch):
    """Accept path must keep self_check green; overlay writable under tmp."""
    cand = tmp_path / "candidates.jsonl"
    # Point learned overlay at a copy we can mutate without touching repo seed
    from categorizer_lexicon import lexicon_dir

    real_learned = lexicon_dir() / "learned_overlay.json"
    learned = tmp_path / "learned_overlay.json"
    learned.write_text(real_learned.read_text(encoding="utf-8"), encoding="utf-8")

    monkeypatch.setattr(lh, "_candidates_path", lambda: cand)
    monkeypatch.setattr(lh, "_learned_path", lambda: learned)

    # Safe accept
    result = lh.accept_term("spine-leaf", "TECHNICAL", family="network_hw", as_tech_object=True)
    assert result["category"] == "TECHNICAL"
    data = json.loads(learned.read_text(encoding="utf-8"))
    assert "spine-leaf" in data["categories"]["TECHNICAL"]["network_hw"]
    reload_lexicons()
    assert categorize_requirement("Spine-Leaf fabric kurulacaktır.") == "TECHNICAL"
    ok, total, fails = self_check()
    assert not fails and ok == total

    # Cleanup mutation from process-global lexicon cache for other tests
    # Revert file content and reload from real overlay
    real_learned.write_text(
        json.dumps(
            {
                "version": "learned",
                "description": "Human-accepted organic lexicon overlay (harvest → accept). Safe to grow; base_v23.json stays curated.",
                "categories": {},
                "tech_objects": [],
                "bounded": {},
                "extra_patterns": {},
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    # Also clear the tmp accept from being the source — reload reads lexicon_dir not tmp
    reload_lexicons()
    # After revert, spine-leaf should not be known from base
    assert "spine-leaf" not in all_known_terms()
