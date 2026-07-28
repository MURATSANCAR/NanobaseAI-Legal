# File Security

Akış: `UPLOADED → QUARANTINED(temp prefix) → SECURITY_SCANNING → SAFE → PROCESSING`.
`MALICIOUS`, `SECURITY_SCAN_FAILED`, `MANUAL_SECURITY_REVIEW` ve `REJECTED` parser event’i
üretmez. `DocumentService` yalnız `FileSecurityService.requireSafe` döndükten sonra object’i
final prefix’e compose eder ve outbox kaydı oluşturur.

Kontroller: servlet ve domain boyut sınırı; Tika MIME/magic + extension uyumu; filename
sanitization; tenant/project duplicate SHA-256; DOCX entry/uncompressed byte/ratio/path/nested
archive sınırı; encrypted/malformed archive reddi; PDF encryption ve yaklaşık page limit;
ClamAV `INSTREAM` gerçek protocol adapter; fail-closed; hash/size finalization; tek-object 5
dakikalık signed URL ve audit.

Unit kanıtı: `ArchiveSafetyInspectorTest`, `ClamAvFileSecurityScannerTest`,
`DocumentServiceTest`. Gerçek ClamAV/EICAR, malformed PDF corpus ve scanner outage runtime
kanıtı bu hostta Docker olmadığı için yoktur.
