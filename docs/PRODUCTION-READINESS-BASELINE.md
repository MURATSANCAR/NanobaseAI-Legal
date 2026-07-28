# Production Readiness Baseline

Tarih: 2026-07-28. Bu tablo yalnız bu çalışma sırasında gözlenen kanıtı kullanır. `mvn test`
79 testi çalıştırdı; frontend build/test 14 testi hedefler. Docker engine bu hostta olmadığı
için Testcontainers, gerçek bağımlılık smoke, image build, recovery ve E2E kanıtı üretilmedi.

| Bileşen | Kod kanıtı | Test kanıtı | Runtime kanıtı | Durum | Kritik açık |
|---|---|---|---|---|---|
| Authentication/authorization | `SecurityConfig`, `JwtCurrentTenant`, Keycloak realm | Access/service ve MVC testleri; JWT hardening henüz gerçek IdP ile koşulmadı | Yok | PARTIALLY_VERIFIED | MFA/token revoke/audience gerçek realm smoke |
| Tenant izolasyonu | Tenant repository metodları, `TenantTransactionFilter` | `ProjectAccessServiceTest`, RLS IT mevcut fakat bu hostta atlandı | Yok | PARTIALLY_VERIFIED | Gerçek DB role ile negatif tenant testi |
| PostgreSQL RLS | V8–V14 `ENABLE/FORCE RLS` | `PlatformInfrastructureIT#rlsBlocksCrossTenantSqlAccess` mevcut, Docker yok | Yok | PARTIALLY_VERIFIED | App/migration/backup rollerinin gerçek grant testi |
| MinIO storage | `MinioObjectStorage`, tenant prefix, kısa signed URL | `MinioObjectStorageTest`; IT mevcut fakat atlandı | Yok | PARTIALLY_VERIFIED | TLS, SSE, versioning, lifecycle ve restore |
| Transactional outbox | `OutboxStore`, publisher, retry/backoff | `OutboxReliabilityTest` geçti | Yok | PARTIALLY_VERIFIED | Broker restart/crash recovery |
| RabbitMQ idempotency | `ConsumerIdempotencyService`, processed message | Birim testi geçti | Yok | PARTIALLY_VERIFIED | Duplicate delivery gerçek broker testi |
| Document processing | State machine, job service, result persistence | İlgili 13 Java testi geçti | Yok | PARTIALLY_VERIFIED | Gerçek 10–1000 sayfa corpus |
| Docling integration | Gerçek FastAPI/Docling servisi ve adapter | Java contract testleri geçti; Python testleri kurulmadı | Yok | PARTIALLY_VERIFIED | Model ağırlıklarıyla container smoke |
| OpenContracts adapter | Adapter/router ve feature flag tanımı | Router/contract birim testleri geçti | Yok | PARTIALLY_VERIFIED | Gerçek OpenContracts deployment yok |
| Clause extraction | Docling provider-neutral result ve persistence | Persistence/contract testleri geçti | Yok | PARTIALLY_VERIFIED | Golden clause ölçümü |
| Requirement extraction | Dinamik processor/schema/grounding | `DynamicAnalysisEnginesTest` geçti | Yok | PARTIALLY_VERIFIED | Lokal model + golden recall/precision |
| Dynamic ontology/terminology | V10 katalogları, JDBC catalog | Dinamik engine testleri geçti | Yok | PARTIALLY_VERIFIED | Global/tenant seed runtime testi atlandı |
| Policy/model/prompt routing | Version tabloları, routing engine, prompt packages | Dinamik engine testleri geçti | Yok | PARTIALLY_VERIFIED | Quality-gate ile aktivasyon entegrasyonu |
| Grounding | Layered validator ve orchestrator source validation | Birim/contract testleri geçti | Yok | PARTIALLY_VERIFIED | Golden grounding oranı |
| Knowledge graph/evidence | Dinamik entity/relation/evidence servisleri | Sprint 5 engine testleri geçti | Yok | PARTIALLY_VERIFIED | Gerçek retrieval corpus |
| Compliance/risk/conflict/impact | Dinamik policy ve strategy katmanları | 16 engine testi geçti | Yok | PARTIALLY_VERIFIED | Golden quality ve büyük yük |
| Workflow/task/approval | V13 şeması ve policy engine kaynakları | Derleniyor; özel workflow integration testi yok | Yok | PARTIALLY_VERIFIED | API/runtime/UAT |
| Report/decision/finalization | V13 immutable snapshot şeması | Derleniyor; uçtan uca test yok | Yok | NOT_VERIFIED | Üretim API/artifact/finalization senaryosu |
| Audit | Append-only trigger, V14 hash chain, tenant API | MVC testi geçti; hash-chain DB testi koşulmadı | Yok | PARTIALLY_VERIFIED | Zincir migration/runtime doğrulaması |
| File quarantine/malware | Temp prefix, preflight, ClamAV INSTREAM, fail-closed | 5 yeni güvenlik testi geçti | Yok | PARTIALLY_VERIFIED | Gerçek ClamAV/EICAR smoke |
| Quota/backpressure/rate limit | Tenant/project tabloları ve policy servisleri | Derleme + upload etkileşim testi | Yok | PARTIALLY_VERIFIED | Gerçek Redis/RabbitMQ saturation |
| AI evaluation/quality gate | V14 result/gate/shadow/canary modeli, evaluator | Quality gate testleri geçti | Yok | PARTIALLY_VERIFIED | Golden dataset ve gerçek evaluation run |
| Backup/restore/offline | Fail-safe script ve dokümanlar | Shell syntax/architecture kontrolü geçti | Yok | NOT_VERIFIED | Gerçek encrypted backup, restore ve offline install |
| Observability/alerting | Actuator, Prometheus, OTel bağımlılıkları | Derleme kanıtı | Yok | PARTIALLY_VERIFIED | Collector/dashboard/alert delivery runtime |
| Operasyon/pilot/AI kalite UI | Admin API ve üç panelli portal | Frontend build/test hedefi | Build çıktısı | PARTIALLY_VERIFIED | Gerçek backend verisiyle browser E2E |

## Servis readiness seviyeleri

| Servis | Mevcut | Hedef | Gerekçe / Level 5 açığı |
|---|---:|---:|---|
| Backend | 1 | 5 | Derleniyor/test ediliyor; gerçek start, recovery, restore ve alert kanıtı yok |
| Frontend | 1 | 5 | Production build var; auth/backend entegrasyon ve recovery yok |
| Document intelligence | 0 | 5 | Kod/container mevcut; image build ve gerçek Docling run yok |
| AI orchestrator | 0 | 5 | Kod/container mevcut; lokal model runtime/evaluation yok |
| PostgreSQL | 0 | 5 | Pinned container tanımı var; bu hostta runtime yok |
| RabbitMQ | 0 | 5 | Pinned container tanımı var; recovery/DLQ runtime yok |
| Redis | 0 | 5 | Pinned container tanımı var; failover/rate limit runtime yok |
| MinIO | 0 | 5 | Pinned container tanımı var; TLS/versioning/restore runtime yok |
| Keycloak | 0 | 5 | Hardened realm/config mevcut; gerçek start ve token lifecycle yok |
| ClamAV | 0 | 5 | Pinned service ve gerçek protocol adapter var; daemon smoke yok |

Kritik servislerin hiçbiri Level 5 kanıtına sahip değildir; production kabulü verilmez.
