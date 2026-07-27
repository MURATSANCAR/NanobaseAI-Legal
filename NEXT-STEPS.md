# Next Steps

## Sprint 1 — Platform MVP tamamlama

1. Portal-Keycloak PKCE login/logout ve token refresh.
2. Portal proje listesi/oluşturma formlarının gerçek API bağlantısı.
3. Drag-and-drop belge yükleme, progress ve durum ekranı.
4. ClamAV, Apache Tika magic-byte kontrolü ve encrypted PDF koruması.
5. Document status consumer ve SSE ilerleme yayını.

## Sprint 2 — Document Intelligence

1. Python/FastAPI worker.
2. OpenContracts adapter implementasyonu.
3. Corpus, upload, parsing, status ve annotation işlemleri.
4. `external_document_mapping` lifecycle yönetimi.
5. Clause ve source-coordinate tabloları.
6. PDF viewer, madde ağacı ve madde detay ekranı.

## Sprint 3 — Hardening

1. PostgreSQL RLS ve tenant kaçışı integration testleri.
2. Outbox `FOR UPDATE SKIP LOCKED`, publisher confirm ve replay yönetimi.
3. Object reconciliation ve lifecycle policy.
4. Audit görüntüleme/indirme kayıtları.
5. Rate limiting, CSP/CORS ve güvenlik test paketi.
