# Audit Integrity

`audit_event` update/delete DB trigger’ıyla append-only’dir ve tenant RLS kullanır. V14 her
tenant için `previous_hash → event_hash` SHA-256 zinciri kurar. Insert trigger zinciri devam
ettirir; saatlik `AuditIntegrityVerifier` tüm tenant zincirlerini kontrol eder ve
`audit_integrity_failure_total` metric’ini günceller.

Payload serializer before/after JSON kullanır; correlation, user, IP, user-agent ve event
metadata taşır. Token, signed URL, doküman/evidence/prompt/model raw content’i taşımamalıdır.

Insert trigger tenant-scoped transaction advisory lock ile aynı chain head’e eşzamanlı yazmayı
serialize eder. Signed/WORM batch archive production öncesi değerlendirilmelidir.
Migration/runtime DB testi Docker yokluğu nedeniyle çalıştırılmadı.
