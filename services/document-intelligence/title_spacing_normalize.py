"""Collapse PDF letter-spacing artifacts in clause titles (and light body cleanup).

Examples:
  "3.1. SU NU CU Tİ P 1 ( 1 5 AD ET)" → "3.1. SUNUCU TİP 1 (15 ADET)"
  "1. İŞ İN KO NU SU" → "1. İŞİN KONUSU"
"""

from __future__ import annotations

import re
from typing import Any

# Prefer longer matches first when re-segmenting a collapsed syllable run.
_LEXICON: tuple[str, ...] = tuple(
    sorted(
        {
            "BAKANLIĞI",
            "MÜDÜRLÜĞÜ",
            "TEKNOLOJİLERİ",
            "ŞARTNAMESİ",
            "ŞARTNAME",
            "TANIMLAR",
            "KOŞULLAR",
            "KAPSAMI",
            "KONUSU",
            "GENEL",
            "SUNUCU",
            "TEKNİK",
            "ALIMI",
            "ALİMİ",
            "ALIM",
            "ALİM",
            "İŞİN",
            "ADET",
            "TİP",
            "HAZİNE",
            "MALİYE",
            "BİLGİ",
            "DONANIM",
            "YAZILIM",
            "YAZILIMLAR",
            "SİSTEM",
            "SİSTEMLER",
            "KURULUM",
            "PERSONEL",
            "YÜKLENİCİ",
            "İDARE",
            "GARANTİ",
            "LİSANS",
            "SERTİFİKA",
            "UYGUN",
            "OLACAKTIR",
            "BULUNACAKTIR",
            "EDİLECEKTİR",
            "YAPILACAKTIR",
            "SAĞLANACAKTIR",
            "HYPERVISOR",
            "ETHERNET",
            "PROCESSOR",
            "İŞLEMCİ",
            "BELGE",
            "KATALOG",
            "DATASHEET",
        },
        key=len,
        reverse=True,
    )
)

_KEEP_ALONE: frozenset[str] = frozenset(
    {
        "ve",
        "veya",
        "ile",
        "bu",
        "şu",
        "da",
        "de",
        "mi",
        "mı",
        "mu",
        "mü",
        "ki",
        "ne",
        "ya",
        "en",
        "az",
        "çok",
        "her",
        "bir",
        "vb",
        "vs",
        "tc",
        "gb",
        "tb",
        "ghz",
        "mhz",
        "cpu",
        "gpu",
        "ram",
        "ssd",
        "hdd",
        "usb",
        "pci",
        "psu",
        "ups",
        "os",
        "vm",
        "ip",
        "ce",
        "iso",
        "tse",
        "ssl",
        "tls",
        "kvkk",
        "eol",
        "eos",
        "raid",
        "dimm",
        "nvme",
        "xeon",
    }
)

_TOKEN_CORE = re.compile(r"^[\dA-Za-zÇĞİÖŞÜçğıöşü]+$", re.UNICODE)
_LEADING_NUM = re.compile(r"^(\d+(?:\.\d+)*\.?)\s+(.*)$", re.UNICODE)
_MULTI_SPACE = re.compile(r"\s+")
_SPACE_AROUND_PARENS = re.compile(r"\(\s+|\s+\)")


def _fold(s: str) -> str:
    return (s or "").casefold()


def _core(token: str) -> str:
    return token.strip(".,;:!?\"'[]{}")


def _punct_parts(token: str) -> tuple[str, str, str]:
    # Keep parentheses as hard barriers (do not strip '(' / ')').
    if token[:1] in "()":
        return token[0], token[1:], ""
    if token[-1:] in "()":
        return "", token[:-1], token[-1]
    core = _core(token)
    if not core:
        return token, "", ""
    start = token.find(core)
    lead = token[:start]
    trail = token[start + len(core) :]
    return lead, core, trail


def _is_collapsible(token: str) -> bool:
    if token in {"(", ")"}:
        return False
    lead, core, _trail = _punct_parts(token)
    # "(15" must stay atomic so it won't merge with a preceding "1".
    if lead == "(":
        return False
    if not core or not _TOKEN_CORE.match(core):
        return False
    # Particles like "ve"/"mü" only when lowercase; uppercase title chunks must join.
    if core == core.lower() and _fold(core) in _KEEP_ALONE:
        return False
    if core.isdigit():
        # Only join single digits ("1 5" → "15"), never re-join "1"+"15".
        return len(core) == 1
    # Allow up to 4-glyph PDF chunks ("LİM", "KNİK") inside spaced runs.
    return 1 <= len(core) <= 4


def _segment_collapsed(joined: str) -> str:
    """Greedy lexicon segmentation over a fully collapsed syllable run."""
    if not joined:
        return joined
    upper = joined.upper()
    # Preserve original casing roughly by slicing the original string.
    parts: list[str] = []
    i = 0
    n = len(joined)
    while i < n:
        matched = False
        for word in _LEXICON:
            w = word.upper()
            if upper.startswith(w, i):
                parts.append(joined[i : i + len(w)])
                i += len(w)
                matched = True
                break
        if matched:
            continue
        # Consume digit run as its own token.
        if joined[i].isdigit():
            j = i + 1
            while j < n and joined[j].isdigit():
                j += 1
            parts.append(joined[i:j])
            i = j
            continue
        # No lexicon hit: take one char and continue (avoids infinite loop);
        # later chars may still form a lexicon word.
        # Better: take remainder as single leftover word if short.
        j = i + 1
        while j < n:
            rest = upper[i:j]
            # If remaining suffix starts with a lexicon word, stop.
            if any(upper.startswith(w.upper(), j) for w in _LEXICON):
                break
            # If extending never helps and we've eaten a lot, break at letter boundary
            j += 1
            if j - i >= 24:
                break
        # Prefer stopping before a lexicon word
        k = i + 1
        best = n
        for word in _LEXICON:
            pos = upper.find(word.upper(), i + 1)
            if pos != -1:
                best = min(best, pos)
        parts.append(joined[i:best])
        i = best
    return " ".join(p for p in parts if p)


def _collapse_span(tokens: list[str]) -> str:
    leads: list[str] = []
    cores: list[str] = []
    trails: list[str] = []
    for tok in tokens:
        lead, core, trail = _punct_parts(tok)
        leads.append(lead)
        cores.append(core)
        trails.append(trail)
    joined = "".join(cores)
    # Pure digit join: "1 5" → "15"
    if joined.isdigit():
        body = joined
    else:
        body = _segment_collapsed(joined)
    lead = leads[0] if leads else ""
    trail = next((t for t in reversed(trails) if t), "")
    return f"{lead}{body}{trail}"


def normalize_spaced_text(text: str) -> str:
    """Collapse letter-spaced artifacts anywhere in text (title or body)."""
    if not text:
        return ""
    stripped = text.strip()
    prefix = ""
    rest = stripped
    m = _LEADING_NUM.match(stripped)
    if m:
        num = m.group(1).strip()
        prefix = num + (" " if not num.endswith(".") else " ")
        if num.endswith("."):
            prefix = num + " "
        else:
            prefix = num + " "
        rest = m.group(2)

    # Standalone parentheses act as hard barriers between collapse runs.
    rest = rest.replace("(", " ( ").replace(")", " ) ")
    tokens = [t for t in rest.split() if t]
    if len(tokens) <= 1:
        return _MULTI_SPACE.sub(" ", stripped).strip()

    rebuilt: list[str] = []
    i = 0
    while i < len(tokens):
        if tokens[i] in {"(", ")"} or not _is_collapsible(tokens[i]):
            rebuilt.append(tokens[i])
            i += 1
            continue
        j = i + 1
        while j < len(tokens) and tokens[j] not in {"(", ")"} and _is_collapsible(tokens[j]):
            j += 1
        span = tokens[i:j]
        if len(span) >= 2:
            rebuilt.append(_collapse_span(span))
        else:
            rebuilt.append(span[0])
        i = j

    body = " ".join(rebuilt)
    body = body.replace("( ", "(").replace(" )", ")")
    body = _MULTI_SPACE.sub(" ", body).strip()
    return (prefix + body).strip()


def normalize_title(title: str) -> str:
    return normalize_spaced_text(title or "")


def normalize_clause_titles(clauses: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Return new clause list with normalized titles and cleaned rawText."""
    out: list[dict[str, Any]] = []
    for clause in clauses or []:
        item = dict(clause)
        old_title = str(item.get("title") or "")
        new_title = normalize_title(old_title)
        item["title"] = new_title[:500]
        raw = str(item.get("rawText") or "")
        if raw:
            item["rawText"] = normalize_spaced_text(raw)
            if item.get("normalizedText"):
                item["normalizedText"] = normalize_spaced_text(str(item["normalizedText"]))
        meta = dict(item.get("metadata") or {})
        if old_title != new_title:
            meta["titleSpacingNormalized"] = True
            meta["titleRaw"] = old_title[:500]
        item["metadata"] = meta
        out.append(item)
    return out
