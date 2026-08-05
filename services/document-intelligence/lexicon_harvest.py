"""Harvest lexicon candidates from şartname requirements (organic growth).

Flow:
  harvest  → categorizer_lexicons/candidates.jsonl  (pending)
  accept   → categorizer_lexicons/learned_overlay.json + mark accepted
  reject   → mark rejected in candidates.jsonl

Never auto-writes into base_v23.json. Run self_check after accept.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from categorizer_lexicon import all_known_terms, lexicon_dir, reload_lexicons
from requirement_categorizer_v2 import categorize_requirement, self_check

CATEGORIES = (
    "SECURITY",
    "DOCUMENT",
    "FINANCIAL",
    "OPERATIONAL",
    "PERSONNEL",
    "SCHEDULE",
    "COMPLIANCE",
    "TECHNICAL",
    "ADMINISTRATIVE",
)

_TOKEN = re.compile(
    r"[A-Za-zÇĞİÖŞÜçğıöşü][A-Za-zÇĞİÖŞÜçğıöşü0-9+./_-]{2,}",
    re.UNICODE,
)

_STOP = {
    "ve", "veya", "ile", "için", "olan", "olarak", "olacak", "olacaktır",
    "bu", "bir", "her", "gibi", "kadar", "daha", "en", "az", "çok", "veya",
    "the", "and", "or", "for", "with", "from", "that", "this", "shall", "must",
    "edilecektir", "yapılacaktır", "sağlanacaktır", "bulunacaktır", "zorunludur",
    "madde", "bent", "fıkra", "şekilde", "kapsamında", "bakımından", "üzerinde",
    "tarafından", "ait", "ilgili", "diğer", "aynı", "sonra", "önce", "üzere",
    "edinilmesi", "edilmesi", "yapılması", "sağlanması", "teslim", "edilecek",
}

# Lightweight category hints from surrounding tokens (department-agnostic).
_HINTS: dict[str, tuple[str, str]] = {
    "şifre": ("SECURITY", "transport_crypto"),
    "ssl": ("SECURITY", "transport_crypto"),
    "firewall": ("SECURITY", "network_perimeter"),
    "kvkk": ("SECURITY", "privacy_kvkk"),
    "yedekleme": ("OPERATIONAL", "backup_dr"),
    "bakım": ("OPERATIONAL", "maintenance"),
    "sla": ("OPERATIONAL", "support_sla"),
    "sunucu": ("TECHNICAL", "compute"),
    "disk": ("TECHNICAL", "storage"),
    "nvme": ("TECHNICAL", "storage"),
    "switch": ("TECHNICAL", "network_hw"),
    "lisans": ("TECHNICAL", "software_license"),
    "teminat": ("FINANCIAL", "bonds_fees"),
    "ceza": ("FINANCIAL", "bonds_fees"),
    "personel": ("PERSONNEL", "roles"),
    "mühendis": ("PERSONNEL", "roles"),
    "tse": ("COMPLIANCE", "product_certs"),
    "sertifika": ("COMPLIANCE", "product_certs"),
    "takvim": ("SCHEDULE", "timing"),
    "termin": ("SCHEDULE", "timing"),
    "ihale": ("ADMINISTRATIVE", "procurement"),
    "yüklenici": ("ADMINISTRATIVE", "procurement"),
    "katalog": ("DOCUMENT", "deliverables"),
    "datasheet": ("DOCUMENT", "deliverables"),
}


def _candidates_path() -> Path:
    return lexicon_dir() / "candidates.jsonl"


def _learned_path() -> Path:
    return lexicon_dir() / "learned_overlay.json"


def _norm_term(term: str) -> str:
    return " ".join((term or "").split()).strip()


def _tokens(text: str) -> list[str]:
    return [m.group(0) for m in _TOKEN.finditer(text or "")]


def _is_noise(term: str) -> bool:
    t = _norm_term(term)
    if len(t) < 3:
        return True
    if t.casefold() in _STOP:
        return True
    if t.isdigit():
        return True
    if re.fullmatch(r"[\d./+-]+", t):
        return True
    # Pure page/clause refs
    if re.fullmatch(r"\d+(\.\d+)+", t):
        return True
    return False


def extract_candidate_terms(text: str) -> list[str]:
    """Unigrams + content bigrams suitable for lexicon growth."""
    toks = [t for t in _tokens(text) if not _is_noise(t)]
    out: list[str] = []
    seen: set[str] = set()
    for t in toks:
        key = t.casefold()
        if key in seen:
            continue
        seen.add(key)
        out.append(t)
    for a, b in zip(toks, toks[1:]):
        if a.casefold() in _STOP or b.casefold() in _STOP:
            continue
        phrase = f"{a} {b}"
        key = phrase.casefold()
        if key in seen or _is_noise(a) or _is_noise(b):
            continue
        seen.add(key)
        out.append(phrase)
    return out


def suggest_category(text: str, term: str) -> tuple[str | None, str | None]:
    blob = f"{text} {term}".casefold()
    for needle, (cat, fam) in _HINTS.items():
        if needle in blob:
            return cat, fam
    # Acronym-ish single token → TECHNICAL by default suggestion
    t = _norm_term(term)
    if " " not in t and t.isupper() and 3 <= len(t) <= 8:
        return "TECHNICAL", "learned"
    return None, None


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        return []
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")


def _load_learned() -> dict[str, Any]:
    path = _learned_path()
    if not path.is_file():
        return {
            "version": "learned",
            "description": "Human-accepted organic lexicon overlay (harvest → accept).",
            "categories": {},
            "tech_objects": [],
            "bounded": {},
            "extra_patterns": {},
        }
    return json.loads(path.read_text(encoding="utf-8"))


def _save_learned(data: dict[str, Any]) -> None:
    path = _learned_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def iter_requirement_texts(payload: Any) -> Iterable[tuple[str, str, str]]:
    """Yield (text, title, category) from parse result / req list / plain dict."""
    if isinstance(payload, dict):
        reqs = payload.get("requirements")
        if isinstance(reqs, list):
            for r in reqs:
                if not isinstance(r, dict):
                    continue
                text = str(r.get("text") or r.get("normalizedText") or "")
                title = str(r.get("title") or "")
                cat = str(r.get("category") or categorize_requirement(text, title=title))
                yield text, title, cat.upper()
            return
        # single requirement
        if "text" in payload or "normalizedText" in payload:
            text = str(payload.get("text") or payload.get("normalizedText") or "")
            title = str(payload.get("title") or "")
            cat = str(payload.get("category") or categorize_requirement(text, title=title))
            yield text, title, cat.upper()
            return
    if isinstance(payload, list):
        for item in payload:
            yield from iter_requirement_texts(item)


def harvest_from_payloads(
    payloads: Iterable[Any],
    *,
    only_other: bool = True,
    min_count: int = 1,
    source: str = "",
) -> list[dict[str, Any]]:
    known = all_known_terms()
    agg: dict[str, dict[str, Any]] = {}

    for payload in payloads:
        for text, title, cat in iter_requirement_texts(payload):
            if only_other and cat != "OTHER":
                continue
            body = f"{title} {text}".strip()
            if len(body) < 20:
                continue
            for term in extract_candidate_terms(body):
                key = term.casefold()
                if key in known:
                    continue
                if _is_noise(term):
                    continue
                row = agg.get(key)
                if row is None:
                    sug_c, sug_f = suggest_category(body, term)
                    row = {
                        "term": _norm_term(term),
                        "count": 0,
                        "suggestedCategory": sug_c,
                        "suggestedFamily": sug_f or "learned",
                        "status": "pending",
                        "examples": [],
                        "sources": [],
                        "updatedAt": datetime.now(timezone.utc).isoformat(),
                    }
                    agg[key] = row
                row["count"] += 1
                if source and source not in row["sources"]:
                    row["sources"].append(source)
                ex = body[:180]
                if ex and ex not in row["examples"] and len(row["examples"]) < 5:
                    row["examples"].append(ex)

    rows = [r for r in agg.values() if r["count"] >= min_count]
    rows.sort(key=lambda r: (-int(r["count"]), str(r["term"]).casefold()))
    return rows


def merge_candidates(existing: list[dict[str, Any]], fresh: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_key = {str(r.get("term") or "").casefold(): dict(r) for r in existing}
    for row in fresh:
        key = str(row.get("term") or "").casefold()
        if not key:
            continue
        cur = by_key.get(key)
        if cur is None:
            by_key[key] = dict(row)
            continue
        # Preserve human decisions
        status = str(cur.get("status") or "pending")
        if status in {"accepted", "rejected"}:
            cur["count"] = int(cur.get("count") or 0) + int(row.get("count") or 0)
            for ex in row.get("examples") or []:
                if ex not in cur.setdefault("examples", []) and len(cur["examples"]) < 5:
                    cur["examples"].append(ex)
            continue
        cur["count"] = int(cur.get("count") or 0) + int(row.get("count") or 0)
        if not cur.get("suggestedCategory") and row.get("suggestedCategory"):
            cur["suggestedCategory"] = row["suggestedCategory"]
            cur["suggestedFamily"] = row.get("suggestedFamily") or "learned"
        for ex in row.get("examples") or []:
            if ex not in cur.setdefault("examples", []) and len(cur["examples"]) < 5:
                cur["examples"].append(ex)
        for src in row.get("sources") or []:
            if src not in cur.setdefault("sources", []):
                cur["sources"].append(src)
        cur["updatedAt"] = datetime.now(timezone.utc).isoformat()
    out = list(by_key.values())
    out.sort(key=lambda r: (-int(r.get("count") or 0), str(r.get("term") or "").casefold()))
    return out


def accept_term(
    term: str,
    category: str,
    *,
    family: str = "learned",
    as_tech_object: bool = False,
    as_bounded: bool = False,
) -> dict[str, Any]:
    cat = category.strip().upper()
    if cat not in CATEGORIES:
        raise ValueError(f"category must be one of {CATEGORIES}")
    term_n = _norm_term(term)
    if not term_n or _is_noise(term_n):
        raise ValueError(f"refusing noisy term: {term!r}")

    # Short singles → bounded automatically
    if " " not in term_n and len(term_n) <= 3:
        as_bounded = True

    learned = _load_learned()
    cats = learned.setdefault("categories", {})
    bucket = cats.setdefault(cat, {})
    fam_terms = list(bucket.get(family) or [])
    if term_n.casefold() not in {t.casefold() for t in fam_terms}:
        fam_terms.append(term_n)
    bucket[family] = fam_terms

    if as_bounded:
        bounded = learned.setdefault("bounded", {})
        b = list(bounded.get(cat) or [])
        if term_n.casefold() not in {x.casefold() for x in b}:
            b.append(term_n)
        bounded[cat] = b

    if as_tech_object or cat == "TECHNICAL":
        techs = list(learned.get("tech_objects") or [])
        if term_n.casefold() not in {x.casefold() for x in techs}:
            # Only add as tech_object when explicitly requested or clearly a noun phrase
            if as_tech_object:
                techs.append(term_n)
                learned["tech_objects"] = techs

    _save_learned(learned)

    # Gate: self_check must stay green
    reload_lexicons()
    ok, total, fails = self_check()
    if fails:
        # rollback term from learned
        fam_terms = [t for t in fam_terms if t.casefold() != term_n.casefold()]
        bucket[family] = fam_terms
        if as_bounded:
            bounded = learned.setdefault("bounded", {})
            bounded[cat] = [
                t for t in (bounded.get(cat) or []) if t.casefold() != term_n.casefold()
            ]
        _save_learned(learned)
        reload_lexicons()
        detail = "; ".join(f"{e}->{g}" for _, e, g in fails[:3])
        raise RuntimeError(f"self_check failed after accept ({ok}/{total}): {detail}")

    # Mark candidate accepted
    rows = _load_jsonl(_candidates_path())
    found = False
    for row in rows:
        if str(row.get("term") or "").casefold() == term_n.casefold():
            row["status"] = "accepted"
            row["acceptedCategory"] = cat
            row["acceptedFamily"] = family
            row["updatedAt"] = datetime.now(timezone.utc).isoformat()
            found = True
    if not found:
        rows.append(
            {
                "term": term_n,
                "count": 1,
                "suggestedCategory": cat,
                "suggestedFamily": family,
                "status": "accepted",
                "acceptedCategory": cat,
                "acceptedFamily": family,
                "examples": [],
                "sources": ["manual"],
                "updatedAt": datetime.now(timezone.utc).isoformat(),
            }
        )
    _write_jsonl(_candidates_path(), rows)
    return {"term": term_n, "category": cat, "family": family, "self_check": f"{ok}/{total}"}


def reject_term(term: str) -> None:
    term_n = _norm_term(term)
    rows = _load_jsonl(_candidates_path())
    for row in rows:
        if str(row.get("term") or "").casefold() == term_n.casefold():
            row["status"] = "rejected"
            row["updatedAt"] = datetime.now(timezone.utc).isoformat()
    _write_jsonl(_candidates_path(), rows)


def cmd_harvest(args: argparse.Namespace) -> int:
    payloads: list[Any] = []
    for path_s in args.input:
        path = Path(path_s)
        raw = path.read_text(encoding="utf-8")
        if path.suffix == ".jsonl":
            for line in raw.splitlines():
                line = line.strip()
                if line:
                    payloads.append(json.loads(line))
        else:
            payloads.append(json.loads(raw))

    fresh = harvest_from_payloads(
        payloads,
        only_other=not args.all_categories,
        min_count=args.min_count,
        source=args.source or ",".join(Path(p).name for p in args.input),
    )
    out = Path(args.out) if args.out else _candidates_path()
    merged = merge_candidates(_load_jsonl(out), fresh)
    _write_jsonl(out, merged)
    pending = sum(1 for r in merged if r.get("status") == "pending")
    print(f"harvested={len(fresh)} total={len(merged)} pending={pending} out={out}")
    for row in merged[: args.top]:
        if row.get("status") != "pending":
            continue
        print(
            f"  [{row.get('count'):3}] {row.get('term')!r} "
            f"→ {row.get('suggestedCategory') or '?'} / {row.get('suggestedFamily')}"
        )
    return 0


def cmd_accept(args: argparse.Namespace) -> int:
    try:
        result = accept_term(
            args.term,
            args.category,
            family=args.family,
            as_tech_object=args.tech_object,
            as_bounded=args.bounded,
        )
    except (ValueError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print("accepted", json.dumps(result, ensure_ascii=False))
    print(f"learned={_learned_path()}")
    return 0


def cmd_reject(args: argparse.Namespace) -> int:
    reject_term(args.term)
    print(f"rejected {args.term!r}")
    return 0


def cmd_status(_args: argparse.Namespace) -> int:
    rows = _load_jsonl(_candidates_path())
    by = defaultdict(int)
    for r in rows:
        by[str(r.get("status") or "pending")] += 1
    learned = _load_learned()
    n_learned = sum(
        len(terms or [])
        for fams in (learned.get("categories") or {}).values()
        for terms in (fams or {}).values()
    )
    print(f"candidates={len(rows)} {dict(by)} learned_terms={n_learned}")
    pending = [r for r in rows if r.get("status") == "pending"]
    pending.sort(key=lambda r: -int(r.get("count") or 0))
    for row in pending[:20]:
        print(
            f"  [{row.get('count'):3}] {row.get('term')!r} "
            f"→ {row.get('suggestedCategory') or '?'} :: {(row.get('examples') or [''])[0][:70]}"
        )
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Organic lexicon harvest / review")
    sub = p.add_subparsers(dest="cmd", required=True)

    h = sub.add_parser("harvest", help="Extract candidates from parse/requirements JSON")
    h.add_argument("--input", "-i", nargs="+", required=True, help="parse result JSON or requirements JSONL")
    h.add_argument("--out", help="candidates.jsonl path (default: categorizer_lexicons/candidates.jsonl)")
    h.add_argument("--min-count", type=int, default=1)
    h.add_argument("--all-categories", action="store_true", help="harvest from all cats, not only OTHER")
    h.add_argument("--source", default="", help="source label stored on candidates")
    h.add_argument("--top", type=int, default=15)
    h.set_defaults(func=cmd_harvest)

    a = sub.add_parser("accept", help="Accept a term into learned_overlay.json")
    a.add_argument("--term", required=True)
    a.add_argument("--category", required=True, choices=CATEGORIES)
    a.add_argument("--family", default="learned")
    a.add_argument("--tech-object", action="store_true", help="also add to tech_objects")
    a.add_argument("--bounded", action="store_true", help="force word-boundary token")
    a.set_defaults(func=cmd_accept)

    r = sub.add_parser("reject", help="Mark candidate rejected")
    r.add_argument("--term", required=True)
    r.set_defaults(func=cmd_reject)

    s = sub.add_parser("status", help="Show pending candidates + learned size")
    s.set_defaults(func=cmd_status)
    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
