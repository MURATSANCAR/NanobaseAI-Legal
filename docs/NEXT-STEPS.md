# Sonraki Adımlar

## Sprint 1 — Canlı altyapı doğrulaması

1. Docker erişimli CI/runner üzerinde `mvn clean verify` ve tam Compose smoke testini
   zorunlu hale getir.
2. Prompttaki eksik container senaryolarını ekle: gerçek PDF/DOCX upload, MinIO object,
   DB/outbox, broker confirm, consumer, duplicate delivery, yeni versiyon, presigned
   URL ve DLQ.
3. Compose healthcheck'lerini Linux/amd64 ve arm64 üzerinde doğrula.
4. Hosted portal için HTTPS backend/Keycloak endpoint'leri, Sites environment
   değişkenleri, Keycloak redirect URI ve CORS allowlist yapılandır.

## Sprint 2 — Doküman güvenliği ve intelligence

1. ClamAV veya eşdeğer gerçek malware tarama port/adapter'ı ekle.
2. Parser'ı kaynak limitli sandbox'a al; encrypted file ve ZIP bomb koruması ekle.
3. OpenContracts adapter'ı gerçek API contract testleriyle tamamla.
4. OCR/provider routing, mapping lifecycle ve gerçek page/clause persistence ekle.

## Sprint 3 — Authorization ve audit sertleştirme

1. PostgreSQL RLS ve tenant context ekle.
2. Keycloak login başarı/başarısızlık event'lerini merkezi append-only/SIEM pipeline'a
   taşı.
3. Kullanıcı aktif/pasif lifecycle ve realm/application user senkronizasyonu ekle.
4. Rate limiting, admin privilege separation ve security integration testleri ekle.

## Sprint 4 — Portal tamamlama

1. Üye rol/yetki düzenleme ve proje update formlarını bağla.
2. Token destekli SSE yaklaşımı veya güvenli same-origin backend-for-frontend ile
   polling yükünü azalt.
3. Accessibility ve gerçek tarayıcı E2E testleri ekle.
4. Firma/ürün alanlarını ilgili modüller geldiğinde feature flag ile etkinleştir.

## Sprint 5 — Operasyonel dayanıklılık

1. Outbox/DLQ operasyon ekranı ve kontrollü replay aracı ekle.
2. Backup/PITR, MinIO versioning/object lock ve restore tatbikatı tanımla.
3. SLO, alert ve dashboard'ları Prometheus/Grafana üzerinde oluştur.
4. Orphan MinIO object reconciliation job'u ekle.

