# Sonraki Adımlar

## Öncelik 1 — Canlı altyapı ve kabul doğrulaması

1. Docker erişimli CI/runner üzerinde `docker compose config/build/up`, health
   kontrolleri ve `mvn clean verify` adımlarını zorunlu hale getir.
2. Eksik container senaryolarını ekle: gerçek PDF/DOCX upload, MinIO finalize/orphan,
   DB/outbox, iki publisher claim yarışı, broker confirm, duplicate delivery,
   parser sonuç persistence, yeni versiyon, reprocess history, presigned URL ve DLQ.
3. Compose healthcheck'lerini Linux/amd64 ve arm64 üzerinde doğrula.
4. RLS için repository filtresini devre dışı bırakmadan doğrudan SQL cross-tenant
   testlerini ve ayrı system-admin role operasyonunu çalıştır.

## Öncelik 2 — Parser güvenliği ve doğruluk

1. ClamAV veya eşdeğer gerçek malware tarama port/adapter'ı ekle.
2. Parser'ı kaynak limitli sandbox'a al; encrypted file ve ZIP bomb koruması ekle.
3. Docling'i Türkçe gerçek ihale PDF/DOCX corpus'uyla doğrula; OCR kalite eşikleri,
   tablo çıktısı, rotation ve çoklu bounding box doğruluğunu ölç.
4. Hukuki madde numaralandırması için hiyerarşi kurallarını iyileştir.
5. OpenContracts adapter'ını hedef sürümün gerçek API contract testleriyle doğrula;
   ancak varsayılan parser yolunu Docling olarak koru.

## Öncelik 3 — Security ve operasyon

1. Keycloak login başarı/başarısızlık event'lerini merkezi append-only/SIEM pipeline'a
   taşı.
2. Login/edge brute-force limit'i, Redis HA ve replica-geneli rate-limit garantisi
   kur.
3. Database application/admin rollerini ayır; backup/PITR, object lock ve restore
   tatbikatı tanımla.
4. Outbox/DLQ kontrollü replay aracı, orphan operasyon görünümü ve alert'ler ekle.

## Öncelik 4 — Portal ve gözlemlenebilirlik

1. Üye rol/yetki düzenleme ve proje update formlarını bağla.
2. Login, upload, processing progress, SSE reconnect/fallback, clause tree, PDF
   highlight, manual review ve tenant unauthorized için gerçek browser E2E ekle.
3. Büyük doküman, çoklu replica, SSE fan-out ve parser throughput ölçümleriyle SLO
   belirle; Prometheus/Grafana dashboard ve alert'leri kur.
4. Sprint 3 kapsamı dışındaki requirement-analysis modülünü ayrı tasarım, migration
   ve kabul sürecinde ele al.
