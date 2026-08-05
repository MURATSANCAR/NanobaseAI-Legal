"""Tests for organic lexicon harvest / accept gate."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

import lexicon_harvest as lh
from categorizer_lexicon import all_known_terms, lexicon_dir, reload_lexicons
from requirement_categorizer_v2 import categorize_requirement, self_check


def test_extract_skips_stopwords():
    terms = lh.extract_candidate_terms(
        "Spine-Leaf topoloji ile NVMe storage sunucu üzerinde bulunacaktır."
    )
    folded = {t.casefold() for t in terms}
    assert any("spine" in t for t in folded)
    assert "ve" not in folded
    assert "ile" not in folded


def test_harvest_other_only():
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
    assert "dimm" not in terms


def test_accept_into_overlay(tmp_path, monkeypatch):
    # Isolated lexicon dir: copy base + empty learned
    src = lexicon_dir()
    dst = tmp_path / "lexicons"
    dst.mkdir()
    shutil.copy(src / "base_v23.json", dst / "base_v23.json")
    (dst / "learned_overlay.json").write_text(
        json.dumps(
            {
                "version": "learned",
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
    monkeypatch.setenv("CATEGORIZER_LEXICON_DIR", str(dst))
    reload_lexicons()

    cand = dst / "candidates.jsonl"
    monkeypatch.setattr(lh, "_candidates_path", lambda: cand)
    # _learned_path uses lexicon_dir() which reads env
    assert lh._learned_path() == dst / "learned_overlay.json"

    result = lh.accept_term("spine-leaf", "TECHNICAL", family="network_hw", as_tech_object=True)
    assert result["category"] == "TECHNICAL"
    assert categorize_requirement("Spine-Leaf fabric kurulacaktır.") == "TECHNICAL"
    ok, total, fails = self_check()
    assert not fails and ok == total
    assert "spine-leaf" in all_known_terms()

    # restore default lexicon dir for other tests in this process
    monkeypatch.delenv("CATEGORIZER_LEXICON_DIR", raising=False)
    reload_lexicons()
