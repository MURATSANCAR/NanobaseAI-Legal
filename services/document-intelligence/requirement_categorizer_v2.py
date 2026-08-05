"""IT / kamu şartname requirement categorizer (v2).

Scans TECHNICAL → COMPLIANCE → SECURITY → DOCUMENT → SCHEDULE → FINANCIAL →
PERSONNEL → OPERATIONAL → ADMINISTRATIVE before falling back to OTHER.

Matching is space-tolerant so PDF artifacts like "don anım" / "ya zı lım"
still hit TECHNICAL.
"""

from __future__ import annotations

import re
from typing import Any, Iterable

# Optional light cleanup before match (does not replace title_spacing_normalize).
try:
    from title_spacing_normalize import normalize_spaced_text as _soft_norm
except Exception:  # pragma: no cover
    def _soft_norm(text: str) -> str:
        return " ".join((text or "").split())


def _flex(term: str) -> str:
    """Allow optional whitespace between characters: 'dimm' → 'd\\s*i\\s*m\\s*m'."""
    parts = [re.escape(ch) for ch in term if not ch.isspace()]
    return r"\s*".join(parts)


def _alt(terms: Iterable[str], *, flex: bool = True) -> str:
    pieces = []
    for term in terms:
        pieces.append(_flex(term) if flex else re.escape(term))
    return "(?:" + "|".join(pieces) + ")"


def _re(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern, re.IGNORECASE | re.UNICODE)


# Priority order is the list order below.
_CATEGORY_RULES: list[tuple[str, re.Pattern[str]]] = [
    (
        "TECHNICAL",
        _re(
            r"(?:"
            + r"|".join(
                [
                    _alt(
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
                            "hyper-v",
                            "hypervisor",
                            "ethernet",
                            "fiber",
                            "sfp",
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
                            "yazılım",
                            "donanım",
                            "lisans",
                            "virtual",
                            "sanal makine",
                            "ipv6",
                            "ipv4",
                            "tcp",
                            "udp",
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
                            "kubernetes",
                            "docker",
                            "işletim sistemi",
                        ]
                    ),
                    # Short tokens: word-bounded only (avoid "os" matching "ISO sertifika").
                    r"\bos\b",
                    r"\bvm\b",
                    r"\bip\b",
                    # "en az N …" quantitative tech specs
                    r"en\s*az\s*\d+",
                    r"\d+\s*(?:gb|tb|ghz|mhz|watt|w|core|çekirdek|adet)\b",
                    r"\b(?:intel|amd|nvidia|broadcom|samsung|micron)\b",
                ]
            )
            + r")"
        ),
    ),
    (
        "COMPLIANCE",
        _re(
            r"(?:"
            + r"|".join(
                [
                    _alt(
                        [
                            "tse",
                            "ce belgesi",
                            "iso 9001",
                            "iso 27001",
                            "iso9001",
                            "iso27001",
                            "sertifika",
                            "sertifikasyon",
                            "uygunluk",
                            "standart",
                            "mevzuat",
                            "eol",
                            "eos",
                            "end of life",
                            "end of support",
                            "etsi",
                        ]
                    ),
                    # Short / ambiguous tokens: hard word boundaries only.
                    r"\bce\b",
                    r"\bul\b",
                    r"\biec\b",
                    r"\biso\b",
                    r"\biso\s*\d{4,5}\b",
                ]
            )
            + r")"
        ),
    ),
    (
        "SECURITY",
        _re(
            _alt(
                [
                    "ssl",
                    "tls",
                    "firewall",
                    "güvenlik duvarı",
                    "kvkk",
                    "5651",
                    "hardening",
                    "siber",
                    "şifre",
                    "encryption",
                    "kript",
                    "yetkilendirme",
                    "kimlik doğrulama",
                    "authentication",
                    "authorization",
                    "sızma",
                    "penetration",
                    "antivirus",
                    "anti-virüs",
                    "siem",
                    "ids",
                    "ips",
                ]
            )
        ),
    ),
    (
        "DOCUMENT",
        _re(
            _alt(
                [
                    "belge",
                    "belgeler",
                    "doküman",
                    "dokumantasyon",
                    "dokümantasyon",
                    "katalog",
                    "datasheet",
                    "data sheet",
                    "kılavuz",
                    "manual",
                    "teknik doküman",
                    "orijinal ambalaj",
                    "fatura",
                    " irsaliye",
                    "teslim tutanağı",
                ]
            )
        ),
    ),
    (
        "SCHEDULE",
        _re(
            _alt(
                [
                    "süre",
                    "takvim",
                    "iş günü",
                    "takvim günü",
                    "teslim süresi",
                    "teslimat süresi",
                    "hafta içinde",
                    "gün içinde",
                    "gecikme",
                    "mücbir sebep",
                    "zaman planı",
                    "termin",
                ]
            )
        ),
    ),
    (
        "FINANCIAL",
        _re(
            _alt(
                [
                    "teminat",
                    "geçici teminat",
                    "kesin teminat",
                    "bedel",
                    "ödeme",
                    "fiyat",
                    "mali",
                    "avans",
                    "cezai şart",
                    "penaltı",
                    "kdv",
                    "fatura bedeli",
                ]
            )
        ),
    ),
    (
        "PERSONNEL",
        _re(
            _alt(
                [
                    "personel",
                    "eğitim",
                    "eğitmen",
                    "uzman",
                    "mühendis",
                    "tekniker",
                    "sertifikalı personel",
                    "proje yöneticisi",
                    "iş güvenliği",
                    "çalışan",
                ]
            )
        ),
    ),
    (
        "OPERATIONAL",
        _re(
            _alt(
                [
                    "kurulum",
                    "montaj",
                    "devreye alma",
                    "bakım",
                    "destek",
                    "7/24",
                    "7x24",
                    "kesinti",
                    "süreklilik",
                    "operasyon",
                    "test",
                    "kabul testi",
                    "yerinde destek",
                    "sla",
                    "servis seviyesi",
                    "arıza",
                    "yedek parça",
                ]
            )
        ),
    ),
    (
        "ADMINISTRATIVE",
        _re(
            _alt(
                [
                    "idare",
                    "idari",
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
                ]
            )
        ),
    ),
]


def categorize_requirement(text: str, *, title: str = "") -> str:
    """Return category for a single requirement / clause body."""
    blob = _soft_norm(f"{title} {text}")
    if not blob:
        return "OTHER"
    # Pad so patterns like " ce " can match edges.
    padded = f" {blob} "
    for name, pattern in _CATEGORY_RULES:
        if pattern.search(padded):
            return name
    return "OTHER"


def categorize_many(
    items: list[dict[str, Any]],
    *,
    text_key: str = "text",
    title_key: str = "title",
    category_key: str = "category",
) -> list[dict[str, Any]]:
    """Annotate/overwrite category on a list of requirement dicts."""
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
