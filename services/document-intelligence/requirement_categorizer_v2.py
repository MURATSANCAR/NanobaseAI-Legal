"""IT / kamu şartname requirement categorizer (v2.3).

Priority (first match wins):
  1 SECURITY
  2 DOCUMENT
  3 FINANCIAL
  4 OPERATIONAL (early: kurulum/konfig, 7/24, yerinde müdahale, garanti süresi boyunca)
  5 PERSONNEL
  6 SCHEDULE (early: N gün/hafta içinde, takvim günü, tamamlanacaktır)
  7 OPERATIONAL (lexicon: bakım, destek, izleme, yedekleme, …)
  8 COMPLIANCE
  9 TECHNICAL (lexicon + "en az N" only with tech object)
 10 SCHEDULE (residual süre/termin)
 11 ADMINISTRATIVE
  — OTHER

Lexicons (department-agnostic, scalable to 10k+ terms):
  categorizer_lexicons/*.json  →  categorizer_lexicon.load_lexicons()

Critical tightenings:
  - "en az N" → TECHNICAL only beside a tech object
  - İstekli … sunulacak belgeler → DOCUMENT (before ADMIN)
  - Gecikme … ceza → FINANCIAL (before SCHEDULE)
  - Kurulum … 30 takvim günü → SCHEDULE
  - Kurulum ve konfigürasyon … personel → OPERATIONAL (early)
  - SSL … 256 bit → SECURITY (not TECHNICAL via "en az")
  - TSE'ye uygun güç kaynağı → COMPLIANCE
  - "iş güvenliği" → PERSONNEL (SECURITY bare-güvenlik lookbehind)
"""

from __future__ import annotations

import re
from typing import Any, Callable

from categorizer_lexicon import load_lexicons, tech_object_pattern

__version__ = "2.3"

try:
    from title_spacing_normalize import normalize_spaced_text as _soft_norm
except Exception:  # pragma: no cover

    def _soft_norm(text: str) -> str:
        return " ".join((text or "").split())


Flags = re.IGNORECASE | re.UNICODE


def _re(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern, Flags)


def _pad(text: str) -> str:
    return f" {_soft_norm(text)} "


_EN_AZ_N = r"en\s*az\s*\d+"
_EN_AZ_YEAR = _re(r"en\s*az\s*\d+\s*(?:yıl|sene|year)")

# Narrow structural early rules (must NOT include bare "kurulum" — that breaks SCHEDULE).
_OPS_EARLY = _re(
    r"(?:"
    r"kurulum\s+ve\s+konfig(?:ür|ur)asyon|"
    r"devreye\s+alma|"
    r"yerinde\s+müdahale|"
    r"yerinde\s+destek|"
    r"garanti\s+süresi\s+boyunca|"
    r"7\s*/\s*24|"
    r"7\s*x\s*24|"
    r"24\s*/\s*7|"
    r"5\s*x\s*8|"
    r"5\s*x\s*9"
    r")"
)

_SCHED_EARLY = _re(
    r"(?:"
    r"\d+\s*(?:iş\s*)?(?:takvim\s*)?gün(?:ü|luk)?\s*içinde|"
    r"\d+\s*hafta(?:\s*içinde)?|"
    r"takvim\s*gün|"
    r"iş\s*gün|"
    r"tamamlanacaktır|"
    r"(?:teslim\s+edilecektir).{0,40}(?:gün|hafta|süre|termin)|"
    r"(?:gün|hafta|süre|termin).{0,40}(?:teslim\s+edilecektir)|"
    r"kurulum.{0,60}(?:takvim\s*)?gün|"
    r"(?:takvim\s*)?gün.{0,40}kurulum|"
    r"süre\s*zarfında|"
    r"en\s*geç\s*\d+"
    r")"
)

Rule = tuple[str, Callable[[str], bool]]


def _lex(name: str):
    return load_lexicons().get(name)


def _en_az_n_tech(padded: str) -> bool:
    tech = tech_object_pattern()
    return (
        re.search(rf"(?:{_EN_AZ_N}.{{0,80}}(?:{tech.pattern}))", padded, Flags) is not None
        or re.search(rf"(?:(?:{tech.pattern}).{{0,80}}{_EN_AZ_N})", padded, Flags) is not None
    )


def _rules() -> list[Rule]:
    sec = _lex("SECURITY")
    doc = _lex("DOCUMENT")
    fin = _lex("FINANCIAL")
    ops = _lex("OPERATIONAL")
    per = _lex("PERSONNEL")
    comp = _lex("COMPLIANCE")
    tech = _lex("TECHNICAL")
    sched = _lex("SCHEDULE")
    admin = _lex("ADMINISTRATIVE")

    def hit(lex, padded: str) -> bool:
        return bool(lex and lex.search(padded))

    return [
        ("SECURITY", lambda p: hit(sec, p)),
        ("DOCUMENT", lambda p: hit(doc, p)),
        ("FINANCIAL", lambda p: hit(fin, p)),
        ("OPERATIONAL", lambda p: _OPS_EARLY.search(p) is not None),
        (
            "PERSONNEL",
            lambda p: hit(per, p) or _EN_AZ_YEAR.search(p) is not None,
        ),
        ("SCHEDULE", lambda p: _SCHED_EARLY.search(p) is not None),
        ("OPERATIONAL", lambda p: hit(ops, p)),
        ("COMPLIANCE", lambda p: hit(comp, p)),
        (
            "TECHNICAL",
            lambda p: hit(tech, p) or _en_az_n_tech(p),
        ),
        ("SCHEDULE", lambda p: hit(sched, p)),
        ("ADMINISTRATIVE", lambda p: hit(admin, p)),
    ]


def categorize_requirement(text: str, *, title: str = "") -> str:
    """Return category for a single requirement / clause body (v2.3)."""
    padded = _pad(f"{title} {text}")
    if padded.strip() == "":
        return "OTHER"
    for name, pred in _rules():
        if pred(padded):
            return name
    return "OTHER"


def categorize_many(
    items: list[dict[str, Any]],
    *,
    text_key: str = "text",
    title_key: str = "title",
    category_key: str = "category",
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for item in items or []:
        row = dict(item)
        row[category_key] = categorize_requirement(
            str(row.get(text_key) or row.get("normalizedText") or ""),
            title=str(row.get(title_key) or ""),
        )
        out.append(row)
    return out


def category_counts(requirements: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for req in requirements or []:
        cat = str(req.get("category") or "OTHER").upper()
        counts[cat] = counts.get(cat, 0) + 1
    return counts


# --- self_check (19 cases) --------------------------------------------------

_SELF_CHECK: list[tuple[str, str]] = [
    ("SSL/TLS ile en az 256 bit şifreleme kullanılacaktır.", "SECURITY"),
    ("İstekli tarafından sunulacak belgeler ekte belirtilmiştir.", "DOCUMENT"),
    ("Katalog ve datasheet belgeleri teslim edilecektir.", "DOCUMENT"),
    ("Gecikme halinde günlük ceza uygulanacaktır.", "FINANCIAL"),
    ("Geçici teminat bedeli sözleşme ile belirlenecektir.", "FINANCIAL"),
    ("Kurulum ve konfigürasyon işleri personel tarafından yapılacaktır.", "OPERATIONAL"),
    ("Sistem 7/24 kesintisiz hizmet verecektir.", "OPERATIONAL"),
    ("Arızada yerinde müdahale sağlanacaktır.", "OPERATIONAL"),
    ("Garanti süresi boyunca ücretsiz destek verilecektir.", "OPERATIONAL"),
    ("Sertifikalı personel görevlendirilecektir.", "PERSONNEL"),
    ("Personelin en az 5 yıl deneyimi olacaktır.", "PERSONNEL"),
    ("Kurulum 30 takvim günü içinde tamamlanacaktır.", "SCHEDULE"),
    ("İşler 15 iş günü içinde bitirilecektir.", "SCHEDULE"),
    ("Bakım, izleme ve yedek parça desteği verilecektir.", "OPERATIONAL"),
    ("TSE'ye uygun güç kaynağı teklif edilecektir.", "COMPLIANCE"),
    ("Ürün TSE ve ISO 9001 sertifikasına uygun olacaktır.", "COMPLIANCE"),
    ("Sunucuda en az 2 adet Intel Xeon Gold ve en az 16 DIMM bulunacaktır.", "TECHNICAL"),
    ("Teklif süresi 90 gündür.", "SCHEDULE"),
    ("İdare ile yüklenici arasındaki sözleşme hükümleri geçerlidir.", "ADMINISTRATIVE"),
]


def self_check() -> tuple[int, int, list[tuple[str, str, str]]]:
    """Return (passed, total, failures[(text, expected, got)])."""
    failures: list[tuple[str, str, str]] = []
    for text, expected in _SELF_CHECK:
        got = categorize_requirement(text)
        if got != expected:
            failures.append((text, expected, got))
    total = len(_SELF_CHECK)
    return total - len(failures), total, failures


if __name__ == "__main__":
    from categorizer_lexicon import lexicon_stats

    ok, total, fails = self_check()
    print(f"self_check {ok}/{total} {'OK' if not fails else 'FAIL'}")
    for text, expected, got in fails:
        print(f"  FAIL expected={expected} got={got} :: {text[:80]}")
    print("lexicon_stats", lexicon_stats())
    smoke = [
        (
            "4.3 Gizlilik: Taraflar işbu sözleşme kapsamında edindikleri bilgileri gizli tutacaktır.",
            "SECURITY",
        ),
        ("Ticari sır niteliğindeki bilgiler üçüncü kişilere açıklanamaz.", "SECURITY"),
        ("WAF, DDoS ve Zero Trust / ZTNA uygulanacaktır.", "SECURITY"),
        ("SSO, LDAP, RBAC ve least privilege zorunludur.", "SECURITY"),
        ("Ransomware, CVE yama ve CIS benchmark uygulanacaktır.", "SECURITY"),
        ("Audit log, syslog ve 5651 kayıtları tutulacaktır.", "SECURITY"),
        ("GDPR, kişisel veri maskeleme ve NDA geçerlidir.", "SECURITY"),
        ("ISO 27002, BGYS, pentest ve zafiyet taraması yapılacaktır.", "SECURITY"),
        ("Güvenlik politikası ve HMAC/SHA-256 kullanılacaktır.", "SECURITY"),
        ("Sunucuda en az 16 DIMM bulunacaktır.", "TECHNICAL"),
        ("All-flash NVMe storage ve 100GbE uplink olacaktır.", "TECHNICAL"),
        ("Yedekleme RPO/RTO değerleri DR planında belirtilir.", "OPERATIONAL"),
        ("Hakediş ve avans teminat bedeli ödenecektir.", "FINANCIAL"),
        ("TSE'ye uygun güç kaynağı teklif edilecektir.", "COMPLIANCE"),
        (
            "Montaj çalışmalarında gerekli iş güvenliği YÜKLENİCİ tarafından sağlanacaktır.",
            "PERSONNEL",
        ),
    ]
    smoke_fail = 0
    for text, expected in smoke:
        got = categorize_requirement(text)
        status = "OK" if got == expected else "FAIL"
        if got != expected:
            smoke_fail += 1
        print(f"smoke {status} expected={expected} got={got} :: {text[:70]}")
    raise SystemExit(0 if not fails and smoke_fail == 0 else 1)
