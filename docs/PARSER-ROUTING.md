# Parser Routing

`DefaultDocumentParserRouter` kararı controller dışında verir.

| Girdi | Karar |
| --- | --- |
| Geçerli DOCX | Docling, OCR disabled |
| Dijital PDF | Docling, OCR auto |
| Scan sinyalli/düşük dijital metin oranlı PDF | Docling, OCR forced |
| Annotation/corpus sync istenmiş ve OpenContracts hazır | OpenContracts |
| Seçilen provider geçici olarak erişilemez | Retry |
| MIME/uzantı uyuşmuyor veya güvenli değil | Manual review |

OpenContracts merkezi parser değildir. Varsayılan PDF/DOCX yolu Docling’dir.
Route kararları `document_parser_route_total` metriğine genişletilebilecek merkezi
bir policy sınırında tutulur.

