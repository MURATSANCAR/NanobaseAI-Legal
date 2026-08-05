"""Scalable category lexicons for requirement_categorizer_v2.

Terms live in categorizer_lexicons/*.json (families → term lists).
Matching uses batched regex (flex for short tokens, escaped phrases for long)
so vocab can grow to tens of thousands without rewriting Python rules.

Structural patterns (en az N + tech object, early OPS/SCHED, lookbehinds)
stay in requirement_categorizer_v2.py.
"""

from __future__ import annotations

import json
import re
from functools import lru_cache
from pathlib import Path
from typing import Any, Iterable

Flags = re.IGNORECASE | re.UNICODE
_BATCH = 350
_FLEX_MAX_LEN = 6  # char-flex only for short tokens (PDF letter-spacing)

_LEXICON_DIR = Path(__file__).resolve().parent / "categorizer_lexicons"


def _flex(term: str) -> str:
    parts = [re.escape(ch) for ch in term if not ch.isspace()]
    return r"\s*".join(parts)


def _phrase(term: str) -> str:
    """Multi-word: allow flexible whitespace; short single tokens: char-flex."""
    t = " ".join(term.split())
    if not t:
        return ""
    if " " in t or len(t) > _FLEX_MAX_LEN:
        return r"\s+".join(re.escape(p) for p in t.split())
    return _flex(t)


def _compile_term_batches(terms: Iterable[str]) -> list[re.Pattern[str]]:
    uniq: list[str] = []
    seen: set[str] = set()
    for raw in terms:
        t = " ".join(str(raw).split()).strip()
        if not t:
            continue
        key = t.casefold()
        if key in seen:
            continue
        seen.add(key)
        uniq.append(t)
    # Longer phrases first inside each batch reduces accidental short shadowing
    uniq.sort(key=lambda s: (-len(s), s.casefold()))
    out: list[re.Pattern[str]] = []
    for i in range(0, len(uniq), _BATCH):
        chunk = [_phrase(t) for t in uniq[i : i + _BATCH]]
        chunk = [c for c in chunk if c]
        if not chunk:
            continue
        out.append(re.compile("(?:" + "|".join(chunk) + ")", Flags))
    return out


def _load_json_files(directory: Path) -> dict[str, Any]:
    merged: dict[str, Any] = {
        "version": "0",
        "categories": {},
        "tech_objects": [],
        "bounded": {},
        "extra_patterns": {},
    }
    if not directory.is_dir():
        return merged
    for path in sorted(directory.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        merged["version"] = str(data.get("version") or merged["version"])
        for cat, families in (data.get("categories") or {}).items():
            bucket = merged["categories"].setdefault(cat, {})
            for fam, terms in (families or {}).items():
                bucket.setdefault(fam, [])
                bucket[fam].extend(terms or [])
        merged["tech_objects"].extend(data.get("tech_objects") or [])
        for cat, terms in (data.get("bounded") or {}).items():
            merged["bounded"].setdefault(cat, [])
            merged["bounded"][cat].extend(terms or [])
        for cat, pats in (data.get("extra_patterns") or {}).items():
            merged["extra_patterns"].setdefault(cat, [])
            merged["extra_patterns"][cat].extend(pats or [])
    return merged


def flatten_category(categories: dict[str, Any], name: str) -> list[str]:
    families = categories.get(name) or {}
    out: list[str] = []
    for terms in families.values():
        out.extend(terms or [])
    return out


def lexicon_stats(data: dict[str, Any] | None = None) -> dict[str, int]:
    data = data or _load_json_files(_LEXICON_DIR)
    stats: dict[str, int] = {}
    for cat in data.get("categories") or {}:
        stats[cat] = len({t.casefold() for t in flatten_category(data["categories"], cat)})
    stats["tech_objects"] = len({t.casefold() for t in (data.get("tech_objects") or [])})
    stats["_total_terms"] = sum(v for k, v in stats.items() if not k.startswith("_"))
    return stats


class CategoryLexicon:
    """Compiled term index for one category (+ optional bounded/extra regex)."""

    __slots__ = ("name", "batches", "extra", "term_count")

    def __init__(
        self,
        name: str,
        terms: list[str],
        *,
        bounded: list[str] | None = None,
        extra_patterns: list[str] | None = None,
    ) -> None:
        self.name = name
        self.batches = _compile_term_batches(terms)
        extras: list[re.Pattern[str]] = []
        for tok in bounded or []:
            extras.append(re.compile(rf"\b{re.escape(tok)}\b", Flags))
        for pat in extra_patterns or []:
            extras.append(re.compile(pat, Flags))
        self.extra = extras
        self.term_count = len({t.casefold() for t in terms if str(t).strip()})

    def search(self, padded: str) -> bool:
        for pat in self.extra:
            if pat.search(padded):
                return True
        for pat in self.batches:
            if pat.search(padded):
                return True
        return False


@lru_cache(maxsize=1)
def load_lexicons() -> dict[str, CategoryLexicon]:
    data = _load_json_files(_LEXICON_DIR)
    cats = data.get("categories") or {}
    bounded = data.get("bounded") or {}
    extras = data.get("extra_patterns") or {}
    out: dict[str, CategoryLexicon] = {}
    for name in cats:
        out[name] = CategoryLexicon(
            name,
            flatten_category(cats, name),
            bounded=list(bounded.get(name) or []),
            extra_patterns=list(extras.get(name) or []),
        )
    return out


@lru_cache(maxsize=1)
def tech_object_pattern() -> re.Pattern[str]:
    data = _load_json_files(_LEXICON_DIR)
    terms = list(data.get("tech_objects") or [])
    # Always include TECHNICAL family nouns as tech objects for "en az N"
    terms.extend(flatten_category(data.get("categories") or {}, "TECHNICAL"))
    batches = _compile_term_batches(terms)
    if not batches:
        return re.compile(r"a^")  # never matches
    if len(batches) == 1:
        return batches[0]
    return re.compile("(?:" + "|".join(f"(?:{b.pattern})" for b in batches) + ")", Flags)


def reload_lexicons() -> dict[str, CategoryLexicon]:
    load_lexicons.cache_clear()
    tech_object_pattern.cache_clear()
    return load_lexicons()
