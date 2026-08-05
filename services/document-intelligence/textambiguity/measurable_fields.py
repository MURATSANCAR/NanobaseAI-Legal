"""Extract measurement / operator / threshold / testCondition from requirement text."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from typing import Any

# "en az 16 adet DIMM" / "minimum 16 DIMM" / "at least 32 GB"
_QTY = re.compile(
    r"(?i)\b(?:en\s+az|minimum|at\s+least|en\s+fazla|maximum|at\s+most|"
    r"en\s+çok|en\s+geç|en\s+erken|equals?|eşit)\b"
    r"[^\d]{0,24}(\d+(?:[.,]\d+)?)\s*"
    r"([A-Za-zÇĞİÖŞÜçğıöşü%][\wÇĞİÖŞÜçğıöşü/%\-]{0,40})?"
)

# "30 takvim günü içinde" / "within 30 calendar days"
_WITHIN = re.compile(
    r"(?i)\b(?:içinde|within|dahilinde)\b|"
    r"(\d+)\s*(takvim\s*günü|iş\s*günü|gün|saat|dakika|calendar\s*days?|days?|hours?|minutes?)"
    r"[^\n]{0,20}\b(?:içinde|within|dahilinde)\b|"
    r"\b(?:içinde|within)\s*(\d+)\s*"
    r"(takvim\s*günü|iş\s*günü|gün|saat|dakika|calendar\s*days?|days?|hours?|minutes?)"
)

_WITHIN_NUM = re.compile(
    r"(?i)(\d+)\s*(takvim\s*_?günü|takvim\s*günü|iş\s*günü|gün|saat|dakika|"
    r"calendar\s*days?|business\s*days?|days?|hours?|minutes?)"
    r"[^\n]{0,24}(?:içinde|within|dahilinde)|"
    r"(?:içinde|within|dahilinde)[^\n]{0,12}"
    r"(\d+)\s*(takvim\s*_?günü|takvim\s*günü|iş\s*günü|gün|saat|dakika|"
    r"calendar\s*days?|business\s*days?|days?|hours?|minutes?)"
)

# Qualitative presence: "uygun olacaktır", "sağlanacaktır", "destekleyecektir"
_PRESENCE = re.compile(
    r"(?i)\b("
    r"uygun\s+olacaktır|sağlanacaktır|destekleyecektir|bulunacaktır|"
    r"yapılacaktır|edilecektir|teslim\s+edilecektir|yerine\s+getirilecektir|"
    r"shall\s+be\s+(?:provided|supported|compliant)|must\s+be\s+(?:provided|supported)"
    r")\b"
)

_GTE = re.compile(r"(?i)\b(?:en\s+az|minimum|at\s+least|≥|>=)\b")
_LTE = re.compile(r"(?i)\b(?:en\s+fazla|en\s+çok|maximum|at\s+most|en\s+geç|≤|<=)\b")
_EQ = re.compile(r"(?i)\b(?:eşit|equals?|==)\b")


@dataclass(frozen=True)
class MeasurableFields:
    operator: str | None = None
    threshold: str | None = None
    measurement: str | None = None
    test_condition: str | None = None
    confidence: float = 0.0
    kind: str = "none"  # quantity | within | presence | none

    def as_attributes(self) -> dict[str, Any]:
        """Keys align with ambiguity policy featureSources JSON pointers."""
        out: dict[str, Any] = {}
        if self.measurement:
            out["measurement"] = self.measurement
        if self.operator:
            out["operator"] = self.operator
        if self.threshold is not None:
            out["acceptanceThreshold"] = self.threshold
        if self.test_condition:
            out["testCondition"] = self.test_condition
        out["measurableConfidence"] = self.confidence
        out["measurableKind"] = self.kind
        return out

    def fields_complete(self) -> bool:
        return bool(
            self.operator
            and self.threshold is not None
            and self.measurement
            and self.test_condition
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _norm_unit(raw: str | None) -> str | None:
    if not raw:
        return None
    u = re.sub(r"\s+", "_", raw.strip().lower())
    u = u.replace("takvim_günü", "takvim_günü").replace("takvimgünü", "takvim_günü")
    mapping = {
        "adet": "adet",
        "gb": "GB",
        "tb": "TB",
        "mhz": "MHz",
        "ghz": "GHz",
        "mbps": "Mbps",
        "gbps": "Gbps",
        "%": "%",
        "yüzde": "%",
        "gun": "gün",
        "gün": "gün",
        "takvim_gunu": "takvim_günü",
        "takvim_günü": "takvim_günü",
        "is_gunu": "iş_günü",
        "iş_günü": "iş_günü",
        "saat": "saat",
        "dakika": "dakika",
        "day": "gün",
        "days": "gün",
        "calendar_day": "takvim_günü",
        "calendar_days": "takvim_günü",
        "hour": "saat",
        "hours": "saat",
        "minute": "dakika",
        "minutes": "dakika",
    }
    return mapping.get(u, u)


def _operator_from_span(span: str) -> str:
    if _LTE.search(span):
        return "<="
    if _GTE.search(span):
        return ">="
    if _EQ.search(span):
        return "=="
    return ">="


def extract_measurable_fields(text: str) -> MeasurableFields:
    """Parse structured measurable signals from free-text requirement language."""
    body = " ".join((text or "").split())
    if not body:
        return MeasurableFields()

    within = _WITHIN_NUM.search(body)
    if within:
        n = within.group(1) or within.group(3)
        unit_raw = within.group(2) or within.group(4)
        unit = _norm_unit(unit_raw)
        return MeasurableFields(
            operator="<=",
            threshold=str(n).replace(",", "."),
            measurement=unit or "süre",
            test_condition=f"within_{unit or 'duration'}",
            confidence=0.92,
            kind="within",
        )

    qty = _QTY.search(body)
    if qty:
        n = qty.group(1).replace(",", ".")
        unit = _norm_unit(qty.group(2))
        # Prefer nearby object tokens after the number (e.g. DIMM)
        measurement = unit
        if not measurement or measurement in {"adet", "piece", "pcs"}:
            trail = body[qty.end() : qty.end() + 48]
            obj = re.search(
                r"(?i)\b([A-Z]{2,}[\w\-]*|[A-Za-zÇĞİÖŞÜçğıöşü]{3,}(?:\s+[A-Za-zÇĞİÖŞÜçğıöşü]{3,})?)\b",
                trail,
            )
            if obj:
                measurement = (unit + " " if unit else "") + obj.group(1).strip()
            elif unit:
                measurement = unit
            else:
                measurement = "quantity"
        span = body[max(0, qty.start() - 24) : qty.end()]
        return MeasurableFields(
            operator=_operator_from_span(span),
            threshold=n,
            measurement=measurement.strip(),
            test_condition="numeric_threshold",
            confidence=0.9,
            kind="quantity",
        )

    if _PRESENCE.search(body):
        return MeasurableFields(
            operator="==",
            threshold="true",
            measurement="presence",
            test_condition="qualitative_presence",
            confidence=0.55,
            kind="presence",
        )

    return MeasurableFields()


def merge_attributes(existing: dict[str, Any] | None, fields: MeasurableFields) -> dict[str, Any]:
    """Fill only blank measurable keys; never overwrite expert/model values."""
    out = dict(existing or {})
    for key, value in fields.as_attributes().items():
        if key in ("measurableConfidence", "measurableKind"):
            out[key] = value
            continue
        cur = out.get(key)
        if cur is None or (isinstance(cur, str) and not cur.strip()):
            out[key] = value
    return out
