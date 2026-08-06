#!/usr/bin/env python3
"""Download real DMO IT tender docs + generate Innova evidence PDFs."""
from __future__ import annotations

import json
import re
import urllib.request
from pathlib import Path

ROOT = Path("/tmp/innova-e2e")
TENDER = ROOT / "tender"
COMPANY = ROOT / "company"
UA = {"User-Agent": "Mozilla/5.0 (compatible; NanobaseE2E/1.0)"}


def fetch(url: str, dest: Path, timeout: int = 90) -> bool:
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            data = r.read()
        if len(data) < 1000:
            print(f"TOO_SMALL {url} {len(data)}")
            return False
        dest.write_bytes(data)
        print(f"OK {dest.name} {len(data)} bytes from {url}")
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL {url}: {exc}")
        return False


def scrape_dmo(ihale: str) -> list[str]:
    url = f"https://www.dmo.gov.tr/Ihale/Detay/{ihale}"
    try:
        req = urllib.request.Request(url, headers=UA)
        html = urllib.request.urlopen(req, timeout=60).read().decode("utf-8", "replace")
        (TENDER / f"dmo-{ihale}.html").write_text(html, encoding="utf-8")
        links = sorted(
            set(re.findall(r'href=["\']([^"\']+\.(?:pdf|PDF))["\']', html, flags=re.I))
        )
        abs_links = []
        for link in links:
            if link.startswith("http"):
                abs_links.append(link)
            elif link.startswith("/"):
                abs_links.append("https://www.dmo.gov.tr" + link)
            else:
                abs_links.append("https://www.dmo.gov.tr/" + link.lstrip("./"))
        print(f"IHALE {ihale} links={len(abs_links)}")
        for l in abs_links:
            print(" ", l)
        return abs_links
    except Exception as exc:  # noqa: BLE001
        print(f"SCRAPE_FAIL {ihale}: {exc}")
        return []


def write_simple_pdf(path: Path, lines: list[str]) -> None:
    """Minimal digital PDF with Helvetica text (ASCII-safe transliteration kept readable)."""
    # Prefer reportlab if available
    try:
        from reportlab.lib.pagesizes import A4
        from reportlab.pdfgen import canvas

        c = canvas.Canvas(str(path), pagesize=A4)
        width, height = A4
        y = height - 50
        c.setFont("Helvetica-Bold", 14)
        for i, line in enumerate(lines):
            font = "Helvetica-Bold" if i == 0 else "Helvetica"
            c.setFont(font, 14 if i == 0 else 11)
            # reportlab needs latin-1-ish; encode Turkish via NFKD fallback
            safe = line.encode("latin-1", "replace").decode("latin-1")
            c.drawString(50, y, safe[:110])
            y -= 18
            if y < 50:
                c.showPage()
                y = height - 50
        c.save()
        print(f"PDF_RL {path.name} {path.stat().st_size}")
        return
    except Exception as exc:  # noqa: BLE001
        print(f"reportlab unavailable ({exc}); using raw PDF writer")

    content_lines = []
    y = 750
    for line in lines:
        safe = "".join(ch if ord(ch) < 128 else "?" for ch in line)
        content_lines.append(f"BT /F1 11 Tf 50 {y} Td ({safe.replace('(', '[').replace(')', ']')}) Tj ET")
        y -= 16
    stream = "\n".join(content_lines).encode("ascii", "replace")
    objs = []
    objs.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objs.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    objs.append(
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>"
    )
    objs.append(f"<< /Length {len(stream)} >>\nstream\n".encode() + stream + b"\nendstream")
    objs.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    out = bytearray(b"%PDF-1.4\n")
    offsets = {0: 0}
    for i, body in enumerate(objs, start=1):
        offsets[i] = len(out)
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs)+1}\n".encode()
    out += b"0000000000 65535 f \n"
    for i in range(1, len(objs) + 1):
        out += f"{offsets[i]:010d} 00000 n \n".encode()
    out += f"trailer<< /Size {len(objs)+1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    path.write_bytes(out)
    print(f"PDF_RAW {path.name} {path.stat().st_size}")


def main() -> None:
    TENDER.mkdir(parents=True, exist_ok=True)
    COMPANY.mkdir(parents=True, exist_ok=True)

    # Prefer existing known-good DMO teknik PDF
    src = Path("/tmp/dmo-sunucu-teknik.pdf")
    if src.exists() and src.stat().st_size > 10000:
        dest = TENDER / "01-teknik-sartname-dmo-sunucu.pdf"
        dest.write_bytes(src.read_bytes())
        print(f"COPIED teknik {dest.stat().st_size}")
    else:
        print("MISSING /tmp/dmo-sunucu-teknik.pdf")

    # Official DMO commercial / administrative buying conditions (idari/ticari)
    fetch(
        "https://www.dmo.gov.tr/Files/IcerikYonetimi/ANKARA/Belgeler/Mevzuat/DMO_Dis_Piyasa_Satinalma_Sartnamesi.pdf",
        TENDER / "02-idari-ticari-sartname-dmo-dis-piyasa.pdf",
    )

    # Tip sözleşme / KİK tip sözleşme approximations from public sources
    # Also try DMO ihale attachments
    for ihale in ("17004", "16149"):
        for link in scrape_dmo(ihale):
            name = Path(link.split("?")[0]).name
            lower = name.lower()
            if "teknik" in lower and not (TENDER / "01-teknik-sartname-dmo-sunucu.pdf").exists():
                fetch(link, TENDER / f"01-teknik-{ihale}-{name}")
            elif "ticari" in lower or "idari" in lower or "ilan" in lower:
                fetch(link, TENDER / f"02-idari-{ihale}-{name}")
            elif "sozlesme" in lower or "sözleşme" in lower or "contract" in lower:
                fetch(link, TENDER / f"03-sozlesme-{ihale}-{name}")

    # Public KIK tip sözleşme (mal alımı) if available via ik.gov.tr CDN mirrors — best effort
    for url, name in [
        (
            "https://www.ihale.gov.tr/Dokumanlar/tip_sozlesme_mal_alimi.pdf",
            "03-tip-sozlesme-mal-alimi-kik.pdf",
        ),
        (
            "https://www.ihale.gov.tr/Dokumanlar/Mal_Alimi_Tip_Sozlesmesi.pdf",
            "03-tip-sozlesme-mal-alimi-kik-alt.pdf",
        ),
    ]:
        if not any(p.name.startswith("03-") for p in TENDER.glob("*.pdf")):
            fetch(url, TENDER / name)

    # If still no contract PDF, synthesize a realistic draft contract for the DMO IT buy
    if not any(p.name.startswith("03-") for p in TENDER.glob("*.pdf")):
        write_simple_pdf(
            TENDER / "03-taslak-sozlesme-dmo-sunucu-alimi.pdf",
            [
                "TASLAK SOZLESME - Sunucu Bilgisayar Alimi",
                "Madde 1 - Taraflar: Idare (DMO / ihtiyac sahibi kurum) ve Yuklenici.",
                "Madde 2 - Konu: Teknik sartnamede tanimli sunucu ve yedekleme cozumlerinin tedariki.",
                "Madde 3 - Sure: Teslimat siparis tarihinden itibaren 60 gun icinde tamamlanir.",
                "Madde 4 - Garanti: En az 36 ay yerinde garanti ve yedek parca destegi.",
                "Madde 5 - Bilgi guvenligi: Yuklenici ISO/IEC 27001 uyumlu surecleri uygular.",
                "Madde 6 - Personel: Kurulum ve entegrasyon icin deneyimli sistem muhendisi atanir.",
                "Madde 7 - CE / TSE: Urunler CE isaretli olmali, ilgili TSE standartlarina uygun olmalidir.",
                "Madde 8 - Gizlilik ve KVKK: Kisisel veri isleme KVKK ve idare politikalarina uygun yapilir.",
                "Madde 9 - Ceza: Gecikme halinde sozlesme bedelinin binde biri oraninda gecikme cezasi.",
                "Madde 10 - Uyusmazlik: Ankara mahkemeleri ve icra daireleri yetkilidir.",
            ],
        )

    # Innova company evidence (synthetic but realistic for fit engine)
    write_simple_pdf(
        COMPANY / "innova-iso27001-certificate.pdf",
        [
            "CERTIFICATE - ISO/IEC 27001:2022",
            "Organization: Innova Bilisim Cozumleri A.S.",
            "Scope: Information Security Management System (BGYS) for IT services,",
            "software development, cloud/managed services and data center operations.",
            "Standard: TS EN ISO/IEC 27001:2022",
            "Certificate No: INN-ISMS-2025-00421",
            "Valid From: 2025-08-01",
            "Valid To: 2028-07-31",
            "Certification Body: Accredited CB (TURKAK aligned)",
            "This certificate confirms Innova operates an ISMS covering confidentiality,",
            "integrity and availability controls including access control, cryptography,",
            "incident response, supplier security and continuous improvement.",
        ],
    )
    write_simple_pdf(
        COMPANY / "innova-iso9001-iso20000-certificate.pdf",
        [
            "CERTIFICATE PACK - Quality and IT Service Management",
            "Organization: Innova Bilisim Cozumleri A.S.",
            "1) ISO 9001:2015 Quality Management System - Valid To: 2027-12-31",
            "2) ISO/IEC 20000-1:2018 IT Service Management - Valid To: 2027-12-31",
            "3) ISO 22301 Business Continuity - Valid To: 2027-06-30",
            "Sites: Ankara HQ, Istanbul delivery center",
            "Evidence: Certified management systems for public-sector IT delivery.",
        ],
    )
    write_simple_pdf(
        COMPANY / "innova-yetki-ve-partnerlik.pdf",
        [
            "YETKI VE PARTNERLIK BEYANI - Innova Bilisim Cozumleri A.S.",
            "Yetkili partner / authorized partner for enterprise server platforms.",
            "Yetkili servis ve garanti destegi: 7x24, yerinde mudahale.",
            "Kamu referanslari: telekom, enerji, finans ve kamu BT projeleri.",
            "Personel: CISSP, CISA, CEH, ISO 27001 Lead Auditor sertifikali kadro.",
            "Teknik kapasite: rack sunucu, blade, SAN/NAS, yedekleme, sanallastirma.",
            "Donanim ornegi: sunucu konfigurasyonunda en az 16 DIMM bellek destegi,",
            "redundant PSU, RAID, 10GbE ag, TPM 2.0 guvenlik modulu.",
            "TSE / CE uygunluk: teklif edilen urunler CE isaretli ve ilgili TSE",
            "standartlarina uygun tedarik edilir.",
            "KVKK ve gizlilik taahhutnamesi imzalanmistir.",
            "Gecerlilik: 31.12.2027",
        ],
    )
    write_simple_pdf(
        COMPANY / "innova-urun-katalog-ozet.pdf",
        [
            "URUN KATALOG OZETI - Innova Enterprise Server Portfolio",
            "Model A: 2U rack server, 2x CPU, en az 16 DIMM, 128GB RAM upgrade path,",
            "NVMe + SAS depolama, dual PSU, iLO/iDRAC yonetim, TPM.",
            "Model B: Yedekleme sunucusu / backup appliance, immutable snapshot,",
            "deduplication, 10GbE, 36 ay yerinde garanti.",
            "Hizmetler: kurulum, migrasyon, izleme, SLA 99.5, yillik bakim.",
            "Guvenlik: disk sifreleme, secure boot, patch yonetimi, SIEM entegrasyonu.",
            "Uyum: ISO 27001 kontrollu teslimat sureci, degisiklik yonetimi.",
        ],
    )

    manifest = {
        "tender": sorted(p.name for p in TENDER.glob("*.pdf")),
        "company": sorted(p.name for p in COMPANY.glob("*.pdf")),
        "sizes": {
            str(p.relative_to(ROOT)): p.stat().st_size
            for p in list(TENDER.glob("*.pdf")) + list(COMPANY.glob("*.pdf"))
        },
    }
    (ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
