# Sprint 6 Güvenlik Notları

- Bütün risk/change tablolarında RLS etkin ve zorunludur.
- Repository sorguları ayrıca `organization_id` filtresi uygular.
- Risk ve conflict persist işlemi grounded source ister.
- Conflict için iki request/persisted source ID eşleşmesi tekrar doğrulanır.
- AI yalnız maksimum 50 seçilmiş source görür; internet/tool/filesystem yetkisi yoktur.
- Document içeriği loglanmaz; log yalnız correlation/profile/error type taşır.
- Authority policy bilinmiyorsa source tercihi yasaktır.
- Clarification ve mitigation yalnız candidate’tır.
- Review mutasyonları audit ve expert feedback üretir.
- Eski version, risk, conflict veya stale kayıt fiziksel olarak silinmez.
- PDF signed URL uygulamanın mevcut kısa TTL mekanizmasını kullanır.

Kalan güvenlik işi: hassas evidence redaction policy’sinin risk source DTO’suna
masking adapter olarak uygulanması ve export endpoint’lerinin ayrı audit
kontrolü. Bunlar `KNOWN-ISSUES.md` içinde açıktır.
