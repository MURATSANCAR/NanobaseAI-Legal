"""Reprocess policy: force modes, new job, preserve prior results."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Literal

ForceMode = Literal[
    "AUTO",
    "FORCE_DOCLING",
    "FORCE_SHORT_CIRCUIT",
    "FORCE_OCR",
]


@dataclass(frozen=True)
class ReprocessPlan:
    forceMode: ForceMode
    createNewJob: bool
    preservePreviousResult: bool
    ocrMode: str
    allowShortCircuit: bool
    preferDocling: bool
    reason: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def decide_reprocess_plan(
    *,
    force_mode: str | None = "AUTO",
    previous_status: str | None = None,
    previous_error_code: str | None = None,
) -> ReprocessPlan:
    """Pure policy for reprocess requests.

    Always creates a new job and keeps the previous result artifact untouched.
    """
    mode = (force_mode or "AUTO").strip().upper()
    if mode not in {"AUTO", "FORCE_DOCLING", "FORCE_SHORT_CIRCUIT", "FORCE_OCR"}:
        mode = "AUTO"

    if mode == "FORCE_OCR":
        return ReprocessPlan(
            forceMode="FORCE_OCR",
            createNewJob=True,
            preservePreviousResult=True,
            ocrMode="FORCED",
            allowShortCircuit=False,
            preferDocling=True,
            reason="operator_forced_ocr",
        )
    if mode == "FORCE_DOCLING":
        return ReprocessPlan(
            forceMode="FORCE_DOCLING",
            createNewJob=True,
            preservePreviousResult=True,
            ocrMode="AUTO",
            allowShortCircuit=False,
            preferDocling=True,
            reason="operator_forced_docling",
        )
    if mode == "FORCE_SHORT_CIRCUIT":
        return ReprocessPlan(
            forceMode="FORCE_SHORT_CIRCUIT",
            createNewJob=True,
            preservePreviousResult=True,
            ocrMode="DISABLED",
            allowShortCircuit=True,
            preferDocling=False,
            reason="operator_forced_short_circuit",
        )

    # AUTO: if previous failure was encryption/corrupt → keep AUTO but no short-circuit guess
    if (previous_error_code or "").upper() in {"PDF_ENCRYPTED", "PDF_CORRUPT"}:
        return ReprocessPlan(
            forceMode="AUTO",
            createNewJob=True,
            preservePreviousResult=True,
            ocrMode="AUTO",
            allowShortCircuit=False,
            preferDocling=True,
            reason="previous_manual_review_error",
        )
    if (previous_status or "").upper() == "READY":
        return ReprocessPlan(
            forceMode="AUTO",
            createNewJob=True,
            preservePreviousResult=True,
            ocrMode="AUTO",
            allowShortCircuit=True,
            preferDocling=False,
            reason="reprocess_ready_document",
        )
    return ReprocessPlan(
        forceMode="AUTO",
        createNewJob=True,
        preservePreviousResult=True,
        ocrMode="AUTO",
        allowShortCircuit=True,
        preferDocling=False,
        reason="default_auto_reprocess",
    )


def apply_plan_to_parse_options(plan: ReprocessPlan) -> dict[str, Any]:
    """Options consumed by should_short_circuit / bounded parser."""
    return {
        "ocrMode": plan.ocrMode,
        "allowShortCircuit": plan.allowShortCircuit,
        "preferDocling": plan.preferDocling,
        "forceMode": plan.forceMode,
        "preservePreviousResult": plan.preservePreviousResult,
    }
