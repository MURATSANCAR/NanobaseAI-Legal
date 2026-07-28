# CODEX Handover — Sprint 3

Tarih: 2026-07-28

## 1. Handover doğrulama sonucu

Önceki handover kod ve çalışan testlerle karşılaştırıldı. Modüler monolith ve temel
uygulama sınırları doğrulandı. Tenant, MinIO, outbox ve append-only audit iddiaları
canlı PostgreSQL/MinIO/RabbitMQ çalıştırılamadığı için kısmi kabul edildi.
OpenContracts izolasyonu ve güncel frontend API bağlantıları kod/contract/build
testleriyle doğrulandı.

Önceki “preview ve madde API’leri frontend’e bağlı” iddiası başlangıç kodunda doğru
değildi: page API yoktu, clause API eksikti ve frontend bunları çağırmıyordu.
Sprint 3'te gerçek page/clause/detail bağlantıları eklendi. Ayrıntı
`docs/HANDOVER-VERIFICATION.md` dosyasındadır.

## 2. Yapılan işler

- Outbox için replica-safe claim/reclaim, confirm, retry/backoff/jitter ve DEAD akışı
- Atomik consumer idempotency
- MinIO temporary upload, hash/boyut doğrulamalı finalize, rollback compensation ve
  orphan reconciliation
- JWT kaynaklı transaction tenant context'i ve PostgreSQL RLS
- Provider-neutral document intelligence port, router, Docling ve OpenContracts
  adapter'ları
- Kalıcı processing job/event/page/clause/table/warning modeli
- State machine, RabbitMQ consumer ve güvenli hata/DLQ akışı
- Yetkili, kalıcı event geçmişinden beslenen SSE ve polling fallback
- Sayfalı doküman/page/clause/table/job API'leri
- PDF.js, clause tree, normalize bounding-box highlight ve detay paneli
- Erişim audit'i, endpoint rate limit'i, metrikler ve architecture kuralları

## 3. Değiştirilen dosyalar

Başlıca gruplar:

- Backend: `src/main/java/com/nanobase/specai/document/**`,
  `integration/outbox/**`, `shared/security/**`, `shared/observability/**`,
  `audit/**`
- Migration: `src/main/resources/db/migration/V5`–`V9`
- Konfigürasyon: `application.yml`, `.env.example`, `compose.yaml`, `pom.xml`
- Python: `services/document-intelligence/**`
- Frontend: `frontend/app/page.tsx`, `frontend/app/globals.css`,
  `frontend/src/modules/documents/api.ts`, package lock'ları ve testler
- Testler: backend unit/contract/architecture/integration, frontend contract ve
  Python API testleri
- Dokümanlar: Sprint 3 teslim listesindeki tüm rehberler

Çalışma ağacında eşzamanlı eklenen `analysis/**` ve V10 requirement-analysis
değişiklikleri Sprint 3 kapsamı değildir; korunmuş, yalnız derleme uyumu sağlanmıştır.

## 4. Yeni migration'lar

| Migration | İçerik |
| --- | --- |
| V5 | Outbox alanları, statüler ve claim/retry indeksleri |
| V6 | `processed_message` ve unique consumer/event |
| V7 | Processing job/event/page/clause/table/warning şeması |
| V8 | Tenant tablolarında RLS + FORCE RLS |
| V9 | Document intelligence erişim indeksleri |

V10 eşzamanlı requirement-analysis çalışmasına aittir ve bu sprint teslimi değildir.

## 5. Yeni API'ler

Document detail/version; sayfalı pages, clauses ve tables; clause/page detail;
processing-job history; reprocess; cancel; signed download URL; document/job SSE
endpoint'leri eklendi. Tam liste `docs/DOCUMENT-API.md` içindedir.

## 6. Parser routing davranışı

DOCX ve dijital PDF Docling'e, scan sinyalli PDF forced OCR Docling'e gider.
OpenContracts yalnız annotation/corpus sync istendiğinde ve hazırsa seçilir.
Geçici provider arızası retry, güvenli işlenemeyen format manual review üretir.
Karar controller'da değil `DocumentParserRouter` sınırındadır.

## 7. Docling entegrasyonu

FastAPI servisinde health, submit, status, result ve cancel endpoint'leri vardır.
Job/correlation state SQLite volume'ünde kalıcıdır. Kaynak MinIO'dan environment
credential'ıyla alınır; tenant key, bucket, boyut, sayfa ve timeout sınırları
uygulanır. Gerçek Docling `DocumentConverter` kullanılır; sahte metin/clause yoktur.

Java adapter HTTP timeout ve circuit breaker uygular. Docker ortamı bulunmadığı için
image ve gerçek PDF/DOCX parse akışı bu makinede çalıştırılmadı.

## 8. OpenContracts entegrasyonu

Adapter provider-neutral port'un arkasındadır. Corpus/document external ID'leri
`external_document_mapping` tablosunda tutulur; domain/provider tipleri ayrıdır.
Circuit breaker vardır ve readiness ana uygulamayı düşürmez. Gerçek hedef API
sözleşmesi canlı doğrulanmadığı için varsayılan olarak disabled'dır; Compose'a
doğrulanmamış/`latest` image eklenmemiştir.

## 9. Outbox ve idempotency çözümü

Claim sorguları transaction içinde `FOR UPDATE SKIP LOCKED` kullanır. `CLAIMED`
publisher ölürse timeout sonrası reclaim edilir. RabbitMQ ACK alınmadan ve
unroutable return olmadığı görülmeden event `PUBLISHED` olmaz. Hatalar konfigüre
edilebilir exponential backoff+jitter ile denenir ve limitte `DEAD` olur.

Consumer `(consumer_name,event_id)` unique kaydıyla atomik claim eder. Tamamlanmış
mesaj ikinci kez işlenmez; FAILED veya stale PROCESSING güvenli yeniden alınabilir.

## 10. RLS çözümü

V8 tenant tablolarında RLS ve FORCE RLS uygular. Request tenant'ı yalnız doğrulanmış
JWT `tenant_id` claim'inden transaction-local PostgreSQL setting'e yazılır.
Background worker event tenant'ını object prefix'iyle doğrular ve aynı DB context'i
kurar. Repository tenant filtreleri ek savunma olarak kalır. Application rolüne
`BYPASSRLS` verilmemelidir.

## 11. Frontend ekranları

Doküman listesinde version, page count, status/stage/progress, provider, OCR,
uploader, tarih, warning/error ve aksiyonlar gösterilir. İnceleme modalı clause tree,
PDF.js viewer + normalize highlight ve clause detail panellerinden oluşur. SSE
bearer-token fetch stream, `Last-Event-ID` reconnect ve polling fallback kullanır.

## 12. Çalıştırılan komutlar

```text
mvn clean verify
npm ci --no-audit --no-fund
npm run lint
npm run test
npm run build
pytest -q services/document-intelligence/test_app.py
python -m py_compile services/document-intelligence/app.py
```

System `docker` komutu yoktu. Resmi standalone Docker Compose v2.39.1 indirilerek
gerekli environment placeholder'larıyla `compose config --quiet` başarıyla
çalıştırıldı. Daemon gerektiren diğer Docker komutları çalıştırılamadı.

## 13. Test sayıları ve sonuçları

- Backend: 39 unit/contract/architecture test geçti.
- Failsafe/Testcontainers: 4 test keşfedildi, Docker yokluğu nedeniyle 4 skip.
- Frontend: `npm ci`, lint ve production build geçti; 8/8 test geçti.
- Python: 4/4 test ve bytecode compile geçti.

Skip edilen testler başarılı sayılmamıştır.

## 14. Docker doğrulama sonucu

System `docker` komutu ve Docker daemon/socket yoktu. Standalone Compose v2.39.1
`config --quiet` başarılıdır. Build/up/ps/logs, backend liveness/readiness curl,
real PostgreSQL migration/RLS, RabbitMQ, MinIO, Redis, Docling image ve full E2E
doğrulanmadı. Sprint kabul listesinin runtime Docker ve integration maddeleri açık
kalır.

## 15. Bilinen hatalar

Canlı provider/infra doğrulaması, gerçek browser PDF görsel testi ve büyük dosya/yük
testi eksiktir. Maven wrapper yoktur. Ayrıntılı liste `docs/KNOWN-ISSUES.md`
dosyasındadır.

## 16. Disabled veya tamamlanmamış alanlar

- OpenContracts varsayılan disabled; gerçek target API contract bekliyor.
- Gerçek malware scanner yok; `VIRUS_SCANNING` yalnız lifecycle stage.
- Docling gerçek document parse bu hostta çalıştırılmadı.
- Container kabul senaryolarının bir bölümü henüz otomatik test değildir.
- Login rate limiting backend'e değil Keycloak/edge'e aittir ve burada kurulmadı.
- Sprint kapsamındaki requirement/risk/uygunluk kararı özellikle üretilmedi.

## 17. Performans ölçümleri

Performans ölçümü yapılmadı. Büyük PDF/DOCX, OCR süresi, parser throughput, outbox
claim contention, RabbitMQ backlog, SSE fan-out ve database indeks planları için
baseline henüz yoktur.

## 18. Güvenlik eksikleri

Malware engine, parser sandbox, encrypted/zip-bomb savunması, production TLS/mTLS,
secret manager, Redis HA/edge rate limit, ayrı database admin role, SIEM aktarımı,
backup/PITR/object lock ve adversarial parser testleri production öncesi zorunludur.
RLS canlı PostgreSQL üzerinde henüz doğrulanmamıştır.

## 19. Sonraki sprint önerisi

Önce Docker erişimli CI'da tüm Sprint 3 kabul akışını kapatın: Compose smoke,
real PDF/DOCX, RLS cross-tenant, iki outbox publisher, duplicate broker event,
MinIO orphan, parser persistence, reprocess history, signed URL ve SSE authorization.
Ardından parser security/quality corpus'u, browser E2E, OpenContracts gerçek contract
ve operasyonel SLO/alert çalışmalarına geçin.
