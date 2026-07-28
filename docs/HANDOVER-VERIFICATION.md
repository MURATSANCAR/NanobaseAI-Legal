# Handover Doğrulaması

Doğrulama tarihi: 2026-07-28. Kaynak: `CODEX-HANDOVER.md`, repository kodu ve bu
çalışma ortamında gerçekten çalıştırılan testler.

| İddia | Kod kanıtı | Test kanıtı | Durum | Açıklama |
| --- | --- | --- | --- | --- |
| Spring Boot modüler monolith | `src/main/java/com/nanobase/specai/{tender,document,audit,integration,shared}` | `ArchitectureTest`, `mvn clean verify` | Doğrulandı | Tek deployable Spring Boot uygulaması içinde paket/modül sınırları var. |
| Tenant izolasyonu | `JwtCurrentTenant`, tenant kapsamlı repository metotları, `TenantTransactionFilter`, V8 RLS | `ProjectAccessServiceTest`, `DocumentServiceTest`, `TenantDatabaseContextTest`; RLS IT Docker yokluğu nedeniyle skip | Kısmi | JWT claim ve repository filtresi test edildi. RLS kodu mevcut fakat gerçek PostgreSQL üzerinde bu ortamda çalıştırılamadı. |
| MinIO yükleme | `MinioObjectStorage`, `DocumentService`, `OrphanObjectReconciliationService` | `MinioObjectStorageTest`, `DocumentServiceTest`; MinIO IT skip | Kısmi | Hash/boyut doğrulamalı copy+delete ve rollback compensation unit testte doğrulandı; canlı MinIO testi çalışmadı. |
| Transactional outbox | `OutboxService`, `OutboxStore`, `OutboxPublisher`, V5 | `OutboxReliabilityTest`; iki publisher IT skip | Kısmi | `FOR UPDATE SKIP LOCKED`, claim timeout, broker confirm, retry/jitter ve DEAD kod/test düzeyinde var. Gerçek PostgreSQL/RabbitMQ yarışı çalışmadı. |
| Audit append-only | V1 `audit_event_no_update` trigger, `AuditService` | `AuditControllerTest`; DB trigger testi Docker nedeniyle çalışmadı | Kısmi | Trigger migration’da mevcut. Gerçek DB mutation reddi bu ortamda doğrulanamadı. |
| OpenContracts izolasyonu | `DocumentIntelligencePort`, `OpenContractsDocumentIntelligenceAdapter`, `ExternalDocumentMappingService` | `ArchitectureTest`, `DocumentIntelligenceAdapterContractTest` | Doğrulandı | Domain provider DTO/SDK tiplerini bilmiyor; external ID mapping tablosunda tutuluyor. |
| Frontend API bağlantıları | `frontend/src/modules/documents/api.ts`, `DocumentReview` | 8 frontend testi, lint ve production build | Doğrulandı | Page/clause/job/cancel/SSE/download API’leri gerçek backend yollarına bağlı; placeholder clause verisi yok. |

## Handover çelişkisi

Önceki handover “preview ve madde API’leri frontend’e bağlı” diyordu. Başlangıç
incelemesinde:

- Backend’de yalnız basit `GET /documents/{id}/clauses` listesi vardı.
- Page endpoint’leri yoktu.
- Frontend `documents/api.ts` clause veya page endpoint’i çağırmıyordu.
- SSE backend’de memory tabanlıydı; frontend yalnız polling kullanıyordu.

Dolayısıyla önceki iddia doğrulama anında doğru değildi. Sprint 3 değişiklikleriyle
sayfalı page/clause API’leri, clause detail, kalıcı processing events, yetkili SSE,
reconnect ve polling fallback eklendi.
