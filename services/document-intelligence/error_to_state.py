"""Map parser/guard errors to document terminal states + audit payloads."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Any


MANUAL_REVIEW_CODES = frozenset(
    {
        "PDF_ENCRYPTED",
        "PDF_CORRUPT",
        "NOT_A_PDF",
        "FILE_TOO_SMALL",
        "ZERO_PAGES",
        "FILE_NOT_FOUND",
        "QUALITY_GATE_FAILED",
        "PARTIAL_PARSE_FAILED",
    }
)
FAILED_CODES = frozenset(
    {
        "FILE_TOO_LARGE",
        "TOO_MANY_PAGES",
        "PAGE_COUNT_LIMIT",
        "FILE_SIZE_LIMIT",
        "SOURCE_SIZE_MISMATCH",
        "PARSER_TIMEOUT",
        "PARSER_FAILED",
        "PARSER_DEPENDENCY_MISSING",
        "DOCUMENT_ORCHESTRATION_TIMEOUT",
        "UNSUPPORTED_FOR_BOUNDED",
        "EMPTY_DOCUMENT",
    }
)

PORTAL_MESSAGE_KEYS = {
    "PDF_ENCRYPTED": "document.error.encrypted",
    "PDF_CORRUPT": "document.error.corrupt",
    "NOT_A_PDF": "document.error.not_pdf",
    "FILE_TOO_SMALL": "document.error.too_small",
    "FILE_TOO_LARGE": "document.error.too_large",
    "ZERO_PAGES": "document.error.zero_pages",
    "TOO_MANY_PAGES": "document.error.too_many_pages",
    "PARSER_TIMEOUT": "document.error.timeout",
    "PARSER_FAILED": "document.error.failed",
}


@dataclass(frozen=True)
class ErrorStateDecision:
    errorCode: str
    terminalStatus: str
    jobStatus: str
    portalMessageKey: str
    safeMessage: str
    manualReview: bool
    auditAction: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def resolve_error_state(
    error_code: str,
    *,
    safe_message: str | None = None,
) -> ErrorStateDecision:
    code = (error_code or "PARSER_FAILED").strip().upper()
    if code in MANUAL_REVIEW_CODES:
        terminal = "MANUAL_REVIEW_REQUIRED"
        job_status = "MANUAL_REVIEW_REQUIRED"
        manual = True
        audit = "PARSER_ROUTED_TO_MANUAL_REVIEW"
    elif code in FAILED_CODES:
        terminal = "FAILED"
        job_status = "FAILED"
        manual = False
        audit = "PARSER_FAILED"
    else:
        terminal = "FAILED"
        job_status = "FAILED"
        manual = False
        audit = "PARSER_FAILED_UNKNOWN"
    message = safe_message or _default_message(code)
    return ErrorStateDecision(
        errorCode=code,
        terminalStatus=terminal,
        jobStatus=job_status,
        portalMessageKey=PORTAL_MESSAGE_KEYS.get(code, "document.error.failed"),
        safeMessage=message,
        manualReview=manual,
        auditAction=audit,
    )


def build_audit_event(
    decision: ErrorStateDecision,
    *,
    job_id: str,
    document_version_id: str | None = None,
    correlation_id: str | None = None,
) -> dict[str, Any]:
    return {
        "action": decision.auditAction,
        "at": datetime.now(timezone.utc).isoformat(),
        "jobId": job_id,
        "documentVersionId": document_version_id,
        "correlationId": correlation_id,
        "errorCode": decision.errorCode,
        "terminalStatus": decision.terminalStatus,
        "portalMessageKey": decision.portalMessageKey,
        "manualReview": decision.manualReview,
        # Never include document content.
    }


def _default_message(code: str) -> str:
    mapping = {
        "PDF_ENCRYPTED": "PDF is encrypted and requires manual review",
        "PDF_CORRUPT": "PDF could not be opened and requires manual review",
        "NOT_A_PDF": "Uploaded file is not a PDF",
        "FILE_TOO_SMALL": "Uploaded file is too small",
        "FILE_TOO_LARGE": "Uploaded file exceeds size limit",
        "ZERO_PAGES": "PDF has zero pages",
        "TOO_MANY_PAGES": "PDF exceeds page limit",
        "PARSER_TIMEOUT": "Document parsing timed out",
        "PARSER_FAILED": "Document parser failed",
    }
    return mapping.get(code, "Document processing failed")
