"""Pre-parse guards for corrupt / encrypted / empty / oversized PDFs.

Raises ParserGuardError with stable error codes that map to MANUAL_REVIEW /
FAILED terminal handling upstream. Never logs document content.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class ParserGuardError(Exception):
    def __init__(self, code: str, message: str, *, terminal: str = "MANUAL_REVIEW"):
        super().__init__(message)
        self.code = code
        self.message = message
        self.terminal = terminal


@dataclass(frozen=True)
class GuardLimits:
    min_bytes: int = 64
    max_bytes: int = int(os.getenv("MAX_FILE_SIZE_BYTES", str(100 * 1024 * 1024)))
    max_pages: int = int(os.getenv("MAX_PAGE_COUNT", "500"))
    max_markdown_chars: int = int(
        os.getenv("PDF_INSPECTOR_MARKDOWN_MAX_CHARS", "500000")
    )


DEFAULT_LIMITS = GuardLimits()


def classify_guard_error(error: ParserGuardError) -> dict[str, Any]:
    """Stable payload for job failure / MANUAL_REVIEW mapping."""
    return {
        "errorCode": error.code,
        "safeMessage": error.message,
        "terminalStatus": error.terminal,
        "manualReview": error.terminal == "MANUAL_REVIEW",
    }


def guard_before_parse(
    path: str | Path,
    *,
    limits: GuardLimits | None = None,
    mime_type: str = "application/pdf",
) -> dict[str, Any]:
    """Validate local file before classification / Docling.

    Returns light metadata (page_count when cheap). Raises ParserGuardError.
    """
    limits = limits or DEFAULT_LIMITS
    file_path = Path(path)
    if not file_path.is_file():
        raise ParserGuardError("FILE_NOT_FOUND", "Source file was not found")

    size = file_path.stat().st_size
    if size < limits.min_bytes:
        raise ParserGuardError(
            "FILE_TOO_SMALL",
            "Source file is too small to be a valid document",
        )
    if size > limits.max_bytes:
        raise ParserGuardError(
            "FILE_TOO_LARGE",
            "Source file exceeds the configured size limit",
            terminal="FAILED",
        )

    if mime_type != "application/pdf":
        return {"sizeBytes": size, "pageCount": None}

    header = file_path.read_bytes()[:8]
    if not header.startswith(b"%PDF"):
        raise ParserGuardError("NOT_A_PDF", "File header is not a PDF")

    try:
        from pypdf import PdfReader
    except ImportError as exc:  # pragma: no cover
        raise ParserGuardError(
            "PARSER_DEPENDENCY_MISSING",
            "PDF reader dependency is unavailable",
            terminal="FAILED",
        ) from exc

    try:
        reader = PdfReader(str(file_path))
    except Exception as exc:  # noqa: BLE001
        raise ParserGuardError(
            "PDF_CORRUPT",
            "PDF could not be opened by the reader",
        ) from exc

    if getattr(reader, "is_encrypted", False):
        # Some readers mark encrypted even when empty password works.
        try:
            unlocked = reader.decrypt("")  # type: ignore[attr-defined]
        except Exception:
            unlocked = 0
        if not unlocked:
            raise ParserGuardError(
                "PDF_ENCRYPTED",
                "PDF is encrypted and cannot be processed automatically",
            )

    page_count = len(reader.pages)
    if page_count <= 0:
        raise ParserGuardError("ZERO_PAGES", "PDF has zero pages")
    if page_count > limits.max_pages:
        raise ParserGuardError(
            "TOO_MANY_PAGES",
            "PDF exceeds the configured page limit",
            terminal="FAILED",
        )

    return {"sizeBytes": size, "pageCount": page_count}


def guard_markdown_size(markdown: str | None, *, limits: GuardLimits | None = None) -> str | None:
    """Drop oversized Markdown (memory safety) instead of failing the job."""
    limits = limits or DEFAULT_LIMITS
    if markdown is None:
        return None
    if len(markdown) > limits.max_markdown_chars:
        return None
    return markdown
