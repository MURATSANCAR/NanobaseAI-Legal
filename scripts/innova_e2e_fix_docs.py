#!/usr/bin/env python3
"""Fix DMO downloads (HTML entities) and generate Innova PDFs with reportlab."""
from __future__ import annotations

import html
import urllib.parse
import urllib.request
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

UA = {"User-Agent": "Mozilla/5.0"}
TENDER = Path("/tmp/innova-e2e/tender")
COMPANY = Path("/tmp/innova-e2e/company")


def get(url: str, dest: Path) -> None:
    url = html.unescape(url)
    parts = urllib.parse.urlsplit(url)
    path = urllib.parse.quote(parts.path)
    url = urllib.parse.urlunsplit(
        (parts.scheme, parts.netloc, path, parts.query, parts.fragment)
    )
    req = urllib.request.Request(url, headers=UA)
    data = urllib.request.urlopen(req, timeout=90).read()
    dest.write_bytes(data)
    print("OK", dest.name, len(data), url)


def write_pdf(path: Path, title: str, paragraphs: list[str], font: str) -> None:
    c = canvas.Canvas(str(path), pagesize=A4)
    _w, h = A4
    y = h - 48
    c.setFont(font, 14)
    c.drawString(48, y, title[:90])
    y -= 28
    c.setFont(font, 11)
    for para in paragraphs:
        for line in para.split("\n"):
            while len(line) > 95:
                c.drawString(48, y, line[:95])
                y -= 15
                line = line[95:]
                if y < 48:
                    c.showPage()
                    c.setFont(font, 11)
                    y = h - 48
            c.drawString(48, y, line)
            y -= 15
            if y < 48:
                c.showPage()
                c.setFont(font, 11)
                y = h - 48
        y -= 8
    c.setFont(font, 10)
    for i in range(40):
        c.drawString(
            48,
            y,
            f"Ek madde {i+1}: Yuklenici teslimat, kurulum, egitim, garanti ve "
            f"dokumantasyon yukumluluklerini yerine getirir.",
        )
        y -= 14
        if y < 48:
            c.showPage()
            c.setFont(font, 10)
            y = h - 48
    c.save()
    print("PDF", path.name, path.stat().st_size)


def main() -> None:
    TENDER.mkdir(parents=True, exist_ok=True)
    COMPANY.mkdir(parents=True, exist_ok=True)

    links = [
        (
            "https://www.dmo.gov.tr/Files/IhaleDosyalari/17004/17004-1-ticari şartname.pdf",
            TENDER / "02-idari-ticari-sartname-dmo-17004.pdf",
        ),
        (
            "https://www.dmo.gov.tr/Files/IhaleDosyalari/17004/17004-2-teknik şartname.pdf",
            TENDER / "01b-teknik-sartname-dmo-17004.pdf",
        ),
        (
            "https://www.dmo.gov.tr/Files/IhaleDosyalari/17004/17004-1-ilan ve ek şartlar.pdf",
            TENDER / "02b-ilan-ek-sartlar-dmo-17004.pdf",
        ),
        (
            "https://www.dmo.gov.tr/Files/IhaleDosyalari/16149/16149-2-ticari şartname.pdf",
            TENDER / "02c-ticari-sartname-dmo-16149.pdf",
        ),
    ]
    for url, dest in links:
        try:
            get(url, dest)
        except Exception as exc:  # noqa: BLE001
            print("FAIL", url, exc)

    src = TENDER / "01b-teknik-sartname-dmo-17004.pdf"
    if src.exists() and src.stat().st_size > 100000:
        dest = TENDER / "01-teknik-sartname-dmo-sunucu.pdf"
        dest.write_bytes(src.read_bytes())
        print("REPLACED teknik with 17004", dest.stat().st_size)

    for cand in [
        TENDER / "02-idari-ticari-sartname-dmo-17004.pdf",
        TENDER / "02c-ticari-sartname-dmo-16149.pdf",
    ]:
        if cand.exists() and cand.stat().st_size > 50000:
            (
                TENDER / "02-idari-ticari-sartname-dmo-dis-piyasa.pdf"
            ).write_bytes(cand.read_bytes())
            print("REPLACED idari with", cand.name, cand.stat().st_size)
            break

    font = "Helvetica"
    for fp in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
    ):
        if Path(fp).exists():
            pdfmetrics.registerFont(TTFont("BodyFont", fp))
            font = "BodyFont"
            break
    print("FONT", font)

    write_pdf(
        TENDER / "03-taslak-sozlesme-dmo-sunucu-alimi.pdf",
        "TASLAK SOZLESME — Sunucu Bilgisayar ve Yedekleme Cozumleri Alimi",
        [
            "Madde 1 — Taraflar: Idare (DMO / ihtiyac sahibi kurum) ile Yuklenici "
            "(Innova Bilisim Cozumleri A.S. teklif sahibi).",
            "Madde 2 — Konu: Teknik sartnamede tanimli sunucu, yedekleme ve ilgili "
            "bilisim urunlerinin tedariki, kurulumu ve garanti hizmetleri.",
            "Madde 3 — Sure: Siparis tarihinden itibaren 60 gun icinde teslimat ve kabul.",
            "Madde 4 — Garanti: En az 36 ay yerinde garanti, yedek parca ve 7x24 destek.",
            "Madde 5 — Bilgi guvenligi: Yuklenici ISO/IEC 27001 uyumlu surecler uygular; "
            "KVKK yukumluluklerine uyar.",
            "Madde 6 — Personel: Kurulum icin deneyimli sistem muhendisi ve guvenlik uzmani.",
            "Madde 7 — Uygunluk: Urunler CE isaretli olmali, ilgili TSE standartlarina uygun olmalidir.",
            "Madde 8 — Gecikme cezasi: Sozlesme bedelinin binde biri / gun.",
            "Madde 9 — Uyusmazlik: Ankara mahkemeleri ve icra daireleri yetkilidir.",
        ],
        font,
    )

    write_pdf(
        COMPANY / "innova-iso27001-certificate.pdf",
        "SERTIFIKA — ISO/IEC 27001:2022 Bilgi Guvenligi Yonetim Sistemi",
        [
            "Kurulus: Innova Bilisim Cozumleri A.S.",
            "Standart: TS EN ISO/IEC 27001:2022",
            "Belge No: INN-ISMS-2025-00421",
            "Gecerlilik: 01.08.2025 — 31.07.2028",
            "Kapsam: Yazilim gelistirme, bulut/managed hizmetler, veri merkezi isletimi, kamu BT.",
            "Kontroller: erisim kontrolu, kriptografi, olay yonetimi, tedarikci guvenligi, KVKK.",
            "Bu belge, Innova BGYS kapsaminda bilgi guvenligi taahhutlerini kanitlar.",
        ],
        font,
    )
    write_pdf(
        COMPANY / "innova-iso9001-iso20000-certificate.pdf",
        "SERTIFIKA PAKETI — ISO 9001 / ISO 20000 / ISO 22301",
        [
            "Kurulus: Innova Bilisim Cozumleri A.S.",
            "ISO 9001:2015 Kalite Yonetim Sistemi — Gecerlilik: 31.12.2027",
            "ISO/IEC 20000-1:2018 BT Hizmet Yonetimi — Gecerlilik: 31.12.2027",
            "ISO 22301 Is Surekliligi — Gecerlilik: 30.06.2027",
            "Lokasyonlar: Ankara merkez, Istanbul teslimat merkezi.",
        ],
        font,
    )
    write_pdf(
        COMPANY / "innova-yetki-ve-partnerlik.pdf",
        "YETKI / PARTNERLIK VE PERSONEL BEYANI — Innova Bilisim Cozumleri A.S.",
        [
            "Yetkili partner belgesi: kurumsal sunucu platformlari icin authorized partner.",
            "Yetkili servis: 7x24 yerinde mudahale ve garanti destegi.",
            "Personel sertifikalari: CISSP, CISA, CEH, ISO 27001 Lead Auditor.",
            "Teknik kapasite: rack sunucu, SAN/NAS, yedekleme, sanallastirma, en az 16 DIMM,",
            "redundant PSU, RAID, 10GbE, TPM 2.0.",
            "TSE / CE uygun urun tedarik taahhudu. KVKK taahhutnamesi imzalanmistir.",
            "Gecerlilik: 31.12.2027",
        ],
        font,
    )
    write_pdf(
        COMPANY / "innova-urun-katalog-ozet.pdf",
        "URUN KATALOGU OZETI — Innova Enterprise Server Portfolio",
        [
            "Model A: 2U rack server, 2x CPU, en az 16 DIMM, 128GB RAM, NVMe+SAS, dual PSU, TPM.",
            "Model B: Yedekleme cihazi, immutable snapshot, deduplication, 10GbE, 36 ay garanti.",
            "Hizmetler: kurulum, migrasyon, izleme, SLA 99.5, yillik bakim.",
            "Guvenlik: disk sifreleme, secure boot, yama yonetimi, SIEM entegrasyonu.",
            "Teslimat: ISO 27001 kontrollu degisiklik ve kabul sureci.",
        ],
        font,
    )

    print("=== FINAL ===")
    for p in sorted(TENDER.glob("*.pdf")) + sorted(COMPANY.glob("*.pdf")):
        print(f"{p.stat().st_size:9} {p.name}")


if __name__ == "__main__":
    main()
