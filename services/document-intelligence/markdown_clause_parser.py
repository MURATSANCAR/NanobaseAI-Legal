"""Hierarchical Markdown clause parser for Turkish tender documents.

Recognizes:
* Markdown headings (# .. ######)
* Madde / MADDE N patterns
* Numeric outlines (1. / 1.2.3 / a) )
* Isolates pipe tables so they do not pollute clause bodies
"""

from __future__ import annotations

import hashlib
import re
from typing import Any

HEADING_RE = re.compile(r"^(#{1,6})\s+(.*)$")
MADDE_RE = re.compile(
    r"^(?:madde|MADDE|Madde)\s*[:\.]?\s*(\d+(?:\.\d+)*)\b(?:\s*[-–—:]\s*(.*))?$",
)
OUTLINE_RE = re.compile(
    r"^("
    r"\d+(?:\.\d+)*\.?"  # 1. / 1.2.3
    r"|[a-zçğıöşüA-ZÇĞİÖŞÜ]\)"  # a) / A)
    r"|[ivxlcdmIVXLCDM]+\."  # i. / IV.
    r")\s+(.*)$"
)
TABLE_LINE_RE = re.compile(r"^\|(.+)\|$")


def _norm(text: str) -> str:
    return " ".join((text or "").split())


def _hash(text: str) -> str:
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()


def _stable_clause_id(clause_number: str, title: str, sort_order: int) -> str:
    seed = f"{clause_number}|{_norm(title)}|{sort_order}"
    return f"md-{_hash(seed)[:16]}"


def _is_table_line(line: str) -> bool:
    stripped = line.strip()
    if not stripped.startswith("|"):
        return False
    return "|" in stripped[1:]


def parse_markdown_clauses(
    markdown: str,
    *,
    page_count: int = 1,
    provider: str = "PDF_INSPECTOR",
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Return (clauses, tables) from Markdown.

    Clauses include hierarchical parentSourceId when outline depth increases.
    Tables are extracted separately and excluded from clause bodies.
    """
    clauses: list[dict[str, Any]] = []
    tables: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None
    parent_stack: list[tuple[int, str]] = []  # (level, sourceId)
    table_buf: list[str] = []
    in_table = False
    sort_order = 0

    def close_clause() -> None:
        nonlocal current, sort_order
        if current is None:
            return
        body = (current.get("rawText") or "").strip()
        if not body:
            current = None
            return
        current["rawText"] = body
        current["normalizedText"] = _norm(body)
        current["contentHash"] = _hash(body)
        current["sortOrder"] = sort_order
        sort_order += 1
        clauses.append(current)
        current = None

    def flush_table() -> None:
        nonlocal table_buf, in_table
        if not table_buf:
            in_table = False
            return
        raw = "\n".join(table_buf)
        tables.append(
            {
                "pageStart": 1,
                "pageEnd": max(1, page_count),
                "caption": None,
                "markdownContent": raw,
                "structuredContent": {"rowCount": len(table_buf)},
                "boundingBoxes": [],
                "contentHash": _hash(raw),
            }
        )
        table_buf = []
        in_table = False

    def open_clause(
        *,
        title: str,
        clause_number: str,
        level: int,
        clause_type: str,
    ) -> None:
        nonlocal current
        close_clause()
        while parent_stack and parent_stack[-1][0] >= level:
            parent_stack.pop()
        parent_id = parent_stack[-1][1] if parent_stack else None
        source_id = _stable_clause_id(clause_number, title, sort_order)
        current = {
            "sourceId": source_id,
            "parentSourceId": parent_id,
            "clauseNumber": clause_number,
            "title": title[:500],
            "rawText": title + "\n",
            "clauseType": clause_type,
            "pageStart": 1,
            "pageEnd": max(1, page_count),
            "boundingBoxes": [],
            "metadata": {
                "provider": provider,
                "source": "MARKDOWN_CLAUSE_PARSER",
                "headingLevel": level,
            },
        }
        parent_stack.append((level, source_id))

    for raw_line in (markdown or "").splitlines():
        line = raw_line.rstrip()
        stripped = line.strip()
        if not stripped:
            if current is not None and not in_table:
                current["rawText"] += "\n"
            continue

        if _is_table_line(stripped):
            if not in_table:
                close_clause()
            in_table = True
            table_buf.append(stripped)
            continue
        if in_table:
            flush_table()

        heading = HEADING_RE.match(stripped)
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip() or f"Section {sort_order + 1}"
            number_match = MADDE_RE.match(title) or OUTLINE_RE.match(title)
            if number_match:
                clause_number = number_match.group(1).rstrip(".")
            else:
                clause_number = f"H{level}.{sort_order + 1}"
            open_clause(
                title=title,
                clause_number=clause_number,
                level=level,
                clause_type=f"HEADING_H{min(level, 6)}",
            )
            continue

        madde = MADDE_RE.match(stripped)
        if madde:
            number = madde.group(1)
            rest = (madde.group(2) or "").strip()
            title = f"Madde {number}" + (f" – {rest}" if rest else "")
            depth = number.count(".") + 1
            open_clause(
                title=title,
                clause_number=number,
                level=depth,
                clause_type="MADDE",
            )
            continue

        outline = OUTLINE_RE.match(stripped)
        if outline:
            number = outline.group(1).rstrip(".")
            rest = (outline.group(2) or "").strip()
            title = f"{number} {rest}".strip()
            depth = number.count(".") + 1 if re.match(r"^\d", number) else 2
            open_clause(
                title=title,
                clause_number=number,
                level=depth,
                clause_type="OUTLINE",
            )
            continue

        if current is None:
            open_clause(
                title=f"Section {sort_order + 1}",
                clause_number=f"C{sort_order + 1}",
                level=1,
                clause_type="BODY",
            )
            if current is not None:
                current["metadata"]["fallback"] = True
                current["rawText"] = ""
        assert current is not None
        current["rawText"] += stripped + "\n"

    if in_table:
        flush_table()
    close_clause()
    return clauses, tables
