"""Page-level capability classification for parser routing.

Classification is content-signal driven (never file-name or tender specific).
"""

from __future__ import annotations

import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Literal

Capability = Literal[
    "NATIVE_TEXT",
    "SCANNED_IMAGE",
    "MIXED_TEXT_IMAGE",
    "TABLE_DOMINANT",
    "VECTOR_COMPLEX",
    "LOW_CONTENT",
    "UNREADABLE",
]

NATIVE_MIN_CHARS = 40
NATIVE_MIN_BLOCKS = 2
REPLACEMENT_CHAR_RATIO_MAX = 0.02
MIXED_MIN_CHARS = 20


@dataclass(frozen=True)
class PageCapability:
    pageNumber: int
    capability: Capability
    nativeTextCharacters: int
    nativeTextBlocks: int
    imageCoverage: float
    imageCount: int
    fontCount: int
    tableLikelihood: float
    vectorObjectComplexity: float
    rotation: int
    renderDurationMs: int
    textExtractionDurationMs: int
    replacementCharRatio: float
    ocrRequired: bool
    whyOcrRequired: str | None
    qualitySignals: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _block_count(text: str) -> int:
    parts = [part.strip() for part in text.replace("\r", "\n").split("\n") if part.strip()]
    return len(parts)


def _replacement_ratio(text: str) -> float:
    if not text:
        return 0.0
    return text.count("\ufffd") / max(1, len(text))


def _table_likelihood(text: str) -> float:
    if not text:
        return 0.0
    lines = [line for line in text.splitlines() if line.strip()]
    if not lines:
        return 0.0
    delimited = sum(1 for line in lines if line.count("|") >= 2 or line.count("\t") >= 2)
    numericish = sum(
        1
        for line in lines
        if sum(ch.isdigit() for ch in line) >= max(4, len(line) // 4)
    )
    return min(1.0, (delimited / len(lines)) * 0.7 + (numericish / len(lines)) * 0.3)


def classify_page_signals(
    *,
    page_number: int,
    text: str,
    image_count: int = 0,
    font_count: int = 0,
    rotation: int = 0,
    text_extraction_ms: int = 0,
    render_ms: int = 0,
    image_coverage: float = 0.0,
    vector_complexity: float = 0.0,
    native_min_chars: int = NATIVE_MIN_CHARS,
) -> PageCapability:
    cleaned = text or ""
    chars = len(cleaned.strip())
    blocks = _block_count(cleaned)
    replacement = _replacement_ratio(cleaned)
    table_likelihood = _table_likelihood(cleaned)
    coverage = image_coverage
    if coverage <= 0.0 and image_count > 0 and chars < native_min_chars:
        coverage = min(1.0, 0.35 * image_count)

    ocr_required = False
    why: str | None = None
    capability: Capability

    if chars == 0 and image_count == 0 and vector_complexity < 0.1:
        capability = "LOW_CONTENT"
        ocr_required = True
        why = "empty_page_no_native_text"
    elif chars < 8 and image_count >= 1:
        capability = "SCANNED_IMAGE"
        ocr_required = True
        why = "image_dominant_no_native_text"
    elif chars < native_min_chars and replacement > REPLACEMENT_CHAR_RATIO_MAX:
        capability = "UNREADABLE"
        ocr_required = True
        why = "replacement_characters_or_corrupt_text"
    elif chars >= native_min_chars and blocks >= NATIVE_MIN_BLOCKS and replacement <= REPLACEMENT_CHAR_RATIO_MAX:
        if table_likelihood >= 0.55:
            capability = "TABLE_DOMINANT"
            ocr_required = False
        elif image_count >= 2 and chars < native_min_chars * 4:
            capability = "MIXED_TEXT_IMAGE"
            ocr_required = chars < MIXED_MIN_CHARS * 3
            why = "mixed_text_and_images" if ocr_required else None
        elif vector_complexity >= 0.8:
            capability = "VECTOR_COMPLEX"
            ocr_required = False
        else:
            capability = "NATIVE_TEXT"
            ocr_required = False
    elif chars >= MIXED_MIN_CHARS and image_count >= 1:
        capability = "MIXED_TEXT_IMAGE"
        ocr_required = True
        why = "insufficient_native_text_with_images"
    elif image_count >= 1:
        capability = "SCANNED_IMAGE"
        ocr_required = True
        why = "scan_or_image_page"
    else:
        capability = "LOW_CONTENT"
        ocr_required = True
        why = "low_native_text"

    return PageCapability(
        pageNumber=page_number,
        capability=capability,
        nativeTextCharacters=chars,
        nativeTextBlocks=blocks,
        imageCoverage=round(coverage, 4),
        imageCount=image_count,
        fontCount=font_count,
        tableLikelihood=round(table_likelihood, 4),
        vectorObjectComplexity=round(vector_complexity, 4),
        rotation=rotation,
        renderDurationMs=render_ms,
        textExtractionDurationMs=text_extraction_ms,
        replacementCharRatio=round(replacement, 6),
        ocrRequired=ocr_required,
        whyOcrRequired=why,
        qualitySignals={
            "nativeMinChars": native_min_chars,
            "coordinateAvailable": False,
            "characterValidity": round(1.0 - replacement, 6),
        },
    )


def classify_pdf_pages(
    path: Path,
    *,
    native_min_chars: int = NATIVE_MIN_CHARS,
) -> list[PageCapability]:
    from pypdf import PdfReader

    reader = PdfReader(str(path))
    pages: list[PageCapability] = []
    for index, page in enumerate(reader.pages, start=1):
        started = time.perf_counter()
        try:
            text = page.extract_text() or ""
        except Exception:
            text = ""
        extraction_ms = int((time.perf_counter() - started) * 1000)
        resources = page.get("/Resources") or {}
        fonts = resources.get("/Font") if hasattr(resources, "get") else None
        font_count = len(fonts) if fonts is not None else 0
        xobject = resources.get("/XObject") if hasattr(resources, "get") else None
        image_count = 0
        if xobject is not None:
            try:
                for _name, ref in xobject.items():
                    try:
                        obj = ref.get_object() if hasattr(ref, "get_object") else ref
                        if str(obj.get("/Subtype", "")) == "/Image":
                            image_count += 1
                    except Exception:
                        continue
            except Exception:
                image_count = 0
        rotation = int(page.get("/Rotate") or 0)
        pages.append(
            classify_page_signals(
                page_number=index,
                text=text,
                image_count=image_count,
                font_count=font_count,
                rotation=rotation,
                text_extraction_ms=extraction_ms,
                native_min_chars=native_min_chars,
            )
        )
    return pages


def build_batches(
    pages: list[PageCapability],
    *,
    batch_size: int,
) -> list[dict[str, Any]]:
    if batch_size < 1:
        raise ValueError("batch_size must be >= 1")
    batches: list[dict[str, Any]] = []
    index = 0
    batch_number = 1
    while index < len(pages):
        seed = pages[index]
        route = "OCR" if seed.ocrRequired else (
            "TABLE" if seed.capability == "TABLE_DOMINANT" else "NATIVE"
        )
        members = [seed]
        cursor = index + 1
        while cursor < len(pages) and len(members) < batch_size:
            candidate = pages[cursor]
            candidate_route = "OCR" if candidate.ocrRequired else (
                "TABLE" if candidate.capability == "TABLE_DOMINANT" else "NATIVE"
            )
            if candidate_route != route:
                break
            members.append(candidate)
            cursor += 1
        page_numbers = [page.pageNumber for page in members]
        batches.append(
            {
                "batchNumber": batch_number,
                "route": route,
                "pageStart": page_numbers[0],
                "pageEnd": page_numbers[-1],
                "pageNumbers": page_numbers,
                "ocrRequired": route == "OCR",
                "capabilities": [page.capability for page in members],
            }
        )
        batch_number += 1
        index = cursor
    return batches
