"""IT / kamu şartname requirement categorizer (v2.2).

Priority (first match wins):
  1 SECURITY
  2 DOCUMENT
  3 FINANCIAL
  4 OPERATIONAL (early: kurulum/konfig, 7/24, yerinde müdahale, garanti süresi boyunca)
  5 PERSONNEL
  6 SCHEDULE (early: N gün/hafta içinde, takvim günü, tamamlanacaktır)
  7 OPERATIONAL (bakım, destek, izleme, yedek parça)
  8 COMPLIANCE
  9 TECHNICAL (tech nouns; "en az N" only with tech object)
 10 SCHEDULE (residual süre/termin)
 11 ADMINISTRATIVE
  — OTHER

Critical tightenings:
  - "en az N" → TECHNICAL only beside a tech object
  - İstekli … sunulacak belgeler → DOCUMENT (before ADMIN)
  - Gecikme … ceza → FINANCIAL (before SCHEDULE)
  - Kurulum … 30 takvim günü → SCHEDULE
  - Kurulum ve konfigürasyon … personel → OPERATIONAL (early)
  - SSL … 256 bit → SECURITY (not TECHNICAL via "en az")
  - TSE'ye uygun güç kaynağı → COMPLIANCE
"""

from __future__ import annotations

import re
from typing import Any, Callable, Iterable

__version__ = "2.2"

try:
    from title_spacing_normalize import normalize_spaced_text as _soft_norm
except Exception:  # pragma: no cover

    def _soft_norm(text: str) -> str:
        return " ".join((text or "").split())


Flags = re.IGNORECASE | re.UNICODE


def _flex(term: str) -> str:
    parts = [re.escape(ch) for ch in term if not ch.isspace()]
    return r"\s*".join(parts)


def _alt(terms: Iterable[str]) -> str:
    return "(?:" + "|".join(_flex(t) for t in terms) + ")"


def _re(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern, Flags)


def _pad(text: str) -> str:
    return f" {_soft_norm(text)} "


# --- shared fragments -------------------------------------------------------

_TECH_OBJECT = _alt(
    [
        "sunucu",
        "server",
        "xeon",
        "epyc",
        "dimm",
        "ssd",
        "nvme",
        "hdd",
        "ram",
        "ecc",
        "vmware",
        "vsan",
        "v-san",
        "hyper-v",
        "hypervisor",
        "ethernet",
        "fiber",
        "sfp",
        "sfp+",
        "raid",
        "bios",
        "uefi",
        "firmware",
        "işlemci",
        "processor",
        "cpu",
        "gpu",
        "anakart",
        "motherboard",
        "psu",
        "güç kaynağı",
        "güç kaynak",
        "yazılım",
        "donanım",
        "lisans",
        "sanal makine",
        "ipv6",
        "ipv4",
        "storage",
        "depolama",
        "disk",
        "slot",
        "pcie",
        "pci-e",
        "backplane",
        "chassis",
        "rack",
        "blade",
        "cluster",
        "port",
        "çekirdek",
        "core",
        "gb",
        "tb",
        "ghz",
        "mhz",
        "watt",
        "işletim sistemi",
        "network",
        "ağ kartı",
        "anakart",
        "bellek",
        "memory",
        "controller",
        "kontrolcü",
        "modül",
        "module",
        "adapter",
        "arabirim",
        "interface",
    ]
)

_EN_AZ_N = r"en\s*az\s*\d+"
_EN_AZ_N_TECH = _re(
    rf"(?:{_EN_AZ_N}.{{0,80}}{_TECH_OBJECT})|(?:{_TECH_OBJECT}.{{0,80}}{_EN_AZ_N})"
)
_EN_AZ_YEAR = _re(r"en\s*az\s*\d+\s*(?:yıl|sene|year)")

# --- rule predicates (order = priority) -------------------------------------

Rule = tuple[str, Callable[[str], bool]]


def _match(pattern: re.Pattern[str], padded: str) -> bool:
    return pattern.search(padded) is not None


_SEC = _re(
    r"(?:"
    + r"|".join(
        [
            _alt(
                [
                    "ssl",
                    "tls",
                    "https",
                    "firewall",
                    "güvenlik duvarı",
                    "kvkk",
                    "5651",
                    "hardening",
                    "siber",
                    "şifre",
                    "şifreleme",
                    "encryption",
                    "encrypt",
                    "kript",
                    "aes",
                    "rsa",
                    "mfa",
                    "2fa",
                    "çok faktörlü",
                    "kimlik doğrulama",
                    "authentication",
                    "authorization",
                    "yetkilendirme",
                    "sızma",
                    "penetration",
                    "antivirus",
                    "anti-virüs",
                    "siem",
                    "ids",
                    "ips",
                    "iso 27001",
                    "iso27001",
                    "256 bit",
                    "128 bit",
                    "tls 1",
                ]
            ),
            r"\bssl\b",
            r"\btls\b",
            r"\bmfa\b",
        ]
    )
    + r")"
)

_DOC = _re(
    r"(?:"
    + r"|".join(
        [
            r"sunulacak\s+belge(?:ler)?",
            r"teslim\s+edilecek\s+belge(?:ler)?",
            r"ibraz\s+edilecek\s+belge",
            _alt(
                [
                    "datasheet",
                    "data sheet",
                    "katalog",
                    "test raporu",
                    "dokümantasyon",
                    "dokumantasyon",
                    "teknik doküman",
                    "kılavuz",
                    "manual",
                    "orijinal ambalaj",
                    "teslim tutanağı",
                    "kullanım kılavuzu",
                ]
            ),
            r"\b(?:belge|belgeler|doküman(?:lar)?)\b",
        ]
    )
    + r")"
)

_FIN = _re(
    r"(?:"
    + r"|".join(
        [
            r"gecikme\s*(?:ceza(?:sı|si)?|bedel(?:i)?|faiz)",
            r"ceza(?:sı|si)?\s*(?:öden|uygulan|kesil)",
            _alt(
                [
                    "teminat",
                    "geçici teminat",
                    "kesin teminat",
                    "bedel",
                    "ödeme",
                    "fiyat",
                    "avans",
                    "cezai şart",
                    "penaltı",
                    "kdv",
                    "sigorta",
                    "fatura bedeli",
                    "hiçbir bedel",
                ]
            ),
            r"\bmali\b",
        ]
    )
    + r")"
)

_OPS_EARLY = _re(
    r"(?:"
    + r"|".join(
        [
            r"kurulum\s+ve\s+konfig(?:ür|ur)asyon",
            r"devreye\s+alma",
            r"yerinde\s+müdahale",
            r"yerinde\s+destek",
            r"garanti\s+süresi\s+boyunca",
            r"7\s*/\s*24",
            r"7\s*x\s*24",
            r"24\s*/\s*7",
            r"5\s*x\s*8",
            r"5\s*x\s*9",
        ]
    )
    + r")"
)

_PERSONNEL = _re(
    r"(?:"
    + r"|".join(
        [
            r"sertifikalı\s+personel",
            r"en\s*az\s*\d+\s*(?:yıl|sene|year)",
            _alt(
                [
                    "personel",
                    "eğitim",
                    "eğitmen",
                    "uzman",
                    "mühendis",
                    "tekniker",
                    "proje yöneticisi",
                    "iş güvenliği",
                    "çalışan",
                    "deneyim",
                    "tecrübe",
                ]
            ),
        ]
    )
    + r")"
)

_SCHED_EARLY = _re(
    r"(?:"
    + r"|".join(
        [
            r"\d+\s*(?:iş\s*)?(?:takvim\s*)?gün(?:ü|luk)?\s*içinde",
            r"\d+\s*hafta(?:\s*içinde)?",
            r"takvim\s*gün",
            r"iş\s*gün",
            r"tamamlanacaktır",
            # "teslim edilecektir" only with an explicit time window nearby
            r"(?:teslim\s+edilecektir).{0,40}(?:gün|hafta|süre|termin)",
            r"(?:gün|hafta|süre|termin).{0,40}(?:teslim\s+edilecektir)",
            r"kurulum.{0,60}(?:takvim\s*)?gün",
            r"(?:takvim\s*)?gün.{0,40}kurulum",
            r"süre\s*zarfında",
            r"en\s*geç\s*\d+",
        ]
    )
    + r")"
)

_OPS = _re(
    r"(?:"
    + r"|".join(
        [
            _alt(
                [
                    "bakım",
                    "destek",
                    "izleme",
                    "monitoring",
                    "yedek parça",
                    "kurulum",
                    "montaj",
                    "operasyon",
                    "kesinti",
                    "süreklilik",
                    "arıza",
                    "servis seviyesi",
                    "kabul testi",
                ]
            ),
            # Short tokens: hard boundaries (avoid "sla" inside "lisansları").
            # Bare "test" omitted — "test edilmiş" is too common in tech clauses.
            r"\bsla\b",
        ]
    )
    + r")"
)

_COMPLIANCE = _re(
    r"(?:"
    + r"|".join(
        [
            _alt(
                [
                    "tse",
                    "ce belgesi",
                    "iso 9001",
                    "iso9001",
                    "iso 27001",
                    "iso27001",
                    "sertifika",
                    "sertifikasyon",
                    "uygunluk",
                    "standart",
                    "mevzuat",
                    "gizlilik",
                    "eol",
                    "eos",
                    "end of life",
                    "end of support",
                    "etsi",
                    "garanti",
                    "tse'ye uygun",
                    "tse ye uygun",
                ]
            ),
            r"\bce\b",
            r"\bul\b",
            r"\biec\b",
            r"\biso\b",
            r"\biso\s*\d{4,5}\b",
            r"tse\s*'?\s*ye\s*uygun",
        ]
    )
    + r")"
)

_TECH = _re(
    r"(?:"
    + r"|".join(
        [
            _TECH_OBJECT,
            r"\b(?:intel|amd|nvidia|broadcom|samsung|micron|dell|hp|lenovo|fujitsu)\b",
            r"\d+\s*(?:gb|tb|ghz|mhz|watt|w|core|çekirdek)\b",
        ]
    )
    + r")"
)

_SCHED = _re(
    r"(?:"
    + r"|".join(
        [
            _alt(
                [
                    "süre",
                    "takvim",
                    "termin",
                    "gecikme",
                    "mücbir sebep",
                    "zaman planı",
                    "teslim süresi",
                    "teslimat süresi",
                ]
            ),
        ]
    )
    + r")"
)

_ADMIN = _re(
    r"(?:"
    + r"|".join(
        [
            _alt(
                [
                    "idare",
                    "idari",
                    "yüklenici",
                    "istekli",
                    "teklif",
                    "ihale",
                    "yeterlik",
                    "iş deneyim",
                    "sözleşme",
                    "zapt",
                    "tutanak",
                    "teslim yeri",
                    "muayene",
                    "kabul komisyonu",
                    "genel husus",
                    "genel koşullar",
                    "işbu şartname",
                ]
            ),
        ]
    )
    + r")"
)


def _rules() -> list[Rule]:
    return [
        ("SECURITY", lambda p: _match(_SEC, p)),
        ("DOCUMENT", lambda p: _match(_DOC, p)),
        ("FINANCIAL", lambda p: _match(_FIN, p)),
        ("OPERATIONAL", lambda p: _match(_OPS_EARLY, p)),
        ("PERSONNEL", lambda p: _match(_PERSONNEL, p) or _match(_EN_AZ_YEAR, p)),
        ("SCHEDULE", lambda p: _match(_SCHED_EARLY, p)),
        ("OPERATIONAL", lambda p: _match(_OPS, p)),
        ("COMPLIANCE", lambda p: _match(_COMPLIANCE, p)),
        (
            "TECHNICAL",
            lambda p: _match(_TECH, p) or _match(_EN_AZ_N_TECH, p),
        ),
        ("SCHEDULE", lambda p: _match(_SCHED, p)),
        ("ADMINISTRATIVE", lambda p: _match(_ADMIN, p)),
    ]


def categorize_requirement(text: str, *, title: str = "") -> str:
    """Return category for a single requirement / clause body (v2.2)."""
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
    ok, total, fails = self_check()
    print(f"self_check {ok}/{total} {'OK' if not fails else 'FAIL'}")
    for text, expected, got in fails:
        print(f"  FAIL expected={expected} got={got} :: {text[:80]}")
