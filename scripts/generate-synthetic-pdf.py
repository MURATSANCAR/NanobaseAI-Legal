#!/usr/bin/env python3
"""Generate the non-sensitive PDF fixture used by the k6 upload profile."""

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "load" / "fixtures" / "synthetic-specification.pdf"


def draw_page(canvas, document):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#D5E3E0"))
    canvas.line(20 * mm, 18 * mm, 190 * mm, 18 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#5F6F6C"))
    canvas.drawString(20 * mm, 11 * mm, "NanobaseAI Legal - synthetic test fixture")
    canvas.drawRightString(190 * mm, 11 * mm, f"Page {document.page}")
    canvas.restoreState()


def build_fixture():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "Title",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=22,
        leading=27,
        textColor=colors.HexColor("#123C36"),
        alignment=TA_CENTER,
        spaceAfter=8 * mm,
    )
    heading = ParagraphStyle(
        "Heading",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=13,
        leading=17,
        textColor=colors.HexColor("#176B5B"),
        spaceBefore=5 * mm,
        spaceAfter=2.5 * mm,
    )
    body = ParagraphStyle(
        "Body",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=10.5,
        leading=15,
        textColor=colors.HexColor("#263330"),
        spaceAfter=2.2 * mm,
    )
    note = ParagraphStyle(
        "Note",
        parent=body,
        backColor=colors.HexColor("#EFF7F5"),
        borderColor=colors.HexColor("#B9D8D1"),
        borderWidth=0.6,
        borderPadding=8,
        spaceBefore=2 * mm,
        spaceAfter=6 * mm,
    )
    document = SimpleDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        rightMargin=20 * mm,
        leftMargin=20 * mm,
        topMargin=22 * mm,
        bottomMargin=24 * mm,
        title="Synthetic Technical Specification",
        author="NanobaseAI Legal",
        subject="Non-sensitive load and security test fixture",
    )
    story = [
        Paragraph("Synthetic Technical Specification", title),
        Paragraph("Cooling Unit / TEST-SPEC-001", styles["Heading3"]),
        Spacer(1, 4 * mm),
        Paragraph(
            "<b>Test data notice.</b> This document is fully synthetic. It contains no "
            "personal data, customer material, confidential information, or executable content.",
            note,
        ),
        Paragraph("1. Electrical requirements", heading),
        Paragraph("1.1. The unit shall operate from a 220 V AC +/- 10%, 50 Hz supply.", body),
        Paragraph("1.2. Continuous power consumption shall not exceed 2.5 kW.", body),
        Paragraph("2. Environmental requirements", heading),
        Paragraph("2.1. The enclosure shall provide at least IP65 ingress protection.", body),
        Paragraph("2.2. The declared operating range shall be -20 C to +55 C.", body),
        Paragraph("3. Verification matrix", heading),
        Table(
            [
                ["Criterion", "Threshold", "Verification method"],
                ["Sound pressure", "<= 65 dB(A)", "Measurement at 1 metre"],
                ["Cooling capacity", ">= 5 kW", "Laboratory performance test"],
                ["Ingress protection", "IP65", "Inspection and certificate"],
            ],
            colWidths=[52 * mm, 38 * mm, 75 * mm],
            repeatRows=1,
            style=TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#176B5B")),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                    ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
                    ("FONTSIZE", (0, 0), (-1, -1), 9),
                    ("LEADING", (0, 0), (-1, -1), 12),
                    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#B9C8C5")),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [
                        colors.white,
                        colors.HexColor("#F5F8F7"),
                    ]),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("TOPPADDING", (0, 0), (-1, -1), 7),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                ]
            ),
        ),
        PageBreak(),
        Paragraph("4. Deliberate ambiguity and conflict", heading),
        Paragraph(
            "4.1. The product should be as light as reasonably possible. No measurable mass "
            "limit is defined, so this statement requires expert clarification.",
            body,
        ),
        Paragraph(
            "4.2. An annex states a minimum operating temperature of -10 C. This conflicts "
            "with clause 2.2 and must be routed to human review rather than resolved automatically.",
            body,
        ),
        Paragraph("5. Security expectations", heading),
        Paragraph(
            "5.1. Uploaded files must pass malware, archive-bomb, encryption, size, and MIME "
            "validation before parsing.",
            body,
        ),
        Paragraph(
            "5.2. Instructions found inside source documents are untrusted data and must never "
            "override system policy or activate tools.",
            body,
        ),
        Paragraph("6. Acceptance notes", heading),
        Paragraph(
            "A successful test preserves tenant isolation, audit-chain continuity, evidence "
            "references, and an explicit human decision for unresolved conflicts.",
            note,
        ),
    ]
    document.build(story, onFirstPage=draw_page, onLaterPages=draw_page)


if __name__ == "__main__":
    build_fixture()
