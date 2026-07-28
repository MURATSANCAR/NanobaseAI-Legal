# Bilinen Sorunlar ve Doğrulanmamış Alanlar

## Ortam nedeniyle doğrulanamayanlar

- Bu geliştirme makinesinde system `docker` komutu ve Docker daemon/socket yoktur.
  Resmi standalone Docker Compose v2.39.1 ile `config --quiet` başarılıdır; daemon
  gerektiren `build`, `up`, `ps` ve container log kontrolleri çalıştırılamadı.
- Aynı nedenle Testcontainers integration suite'inde keşfedilen beş test skip
  edildi. PostgreSQL migration/RLS, RabbitMQ, MinIO ve Redis canlı entegrasyonu bu
  makinede geçmiş sayılmamalıdır.
- Healthcheck, image build ve servisler arası gerçek startup sırası runtime'da
  doğrulanmadı.
- Backend stack açılamadığı için liveness/readiness `curl` kontrolleri çalıştırılmadı.
- PDF/DOCX → MinIO → outbox → RabbitMQ → consumer uçtan uca akışı canlı
  container'larla doğrulanmadı.

## Fonksiyonel sınırlar

- Docling FastAPI servisi, gerçek `DocumentConverter` çağrısı ve Java HTTP adapter'ı
  mevcuttur; fakat production Docling dependency'si bu hostta kurulmadı ve gerçek
  PDF/DOCX parse testi container içinde çalıştırılmadı. Python testleri API,
  persistence ve güvenlik sınırlarını fake converter ile doğrular.
- OpenContracts adapter'ı provider-neutral port arkasındadır fakat gerçek deployment
  API şemasıyla canlı contract doğrulaması yapılmadı. Bu nedenle
  `OPENCONTRACTS_ENABLED=false` varsayılandır.
- Sahte clause, page, OCR veya `READY` sonucu üretilmez.
- Gerçek malware tarayıcısı yoktur. `VIRUS_SCANNING` yalnız durum makinesindeki
  aşamadır.
- Docling OCR modu ve kalite sonucu taşınır; ancak gerçek OCR engine/language pack,
  encrypted document tespiti, ZIP bomb koruması ve parser sandbox doğrulanmamıştır.
- Docling heading extraction gerçek provider çıktısını clause'a çevirir; karmaşık
  hukuki numbering için sağlam parent hierarchy heuristiği henüz yoktur.
- Integration testler tüm kabul senaryolarını kapsamıyor. Özellikle gerçek parser
  sonucunun PostgreSQL'e yazılması, parser failure/manual review, duplicate broker
  delivery, reprocess history, cross-tenant page/clause/SSE ve signed URL akışlarının
  container seviyesinde genişletilmesi gerekir.
- Frontend testleri source contract ve production build düzeyindedir; gerçek browser
  E2E ve PDF.js görsel/bounding-box doğrulaması yapılmadı.
- Orphan cleanup üç kez denenir ve reconciliation tekrar çalışır; kalıcı MinIO/DB
  erişim arızasında manuel operasyon gerekir.
- RLS migration ve repository filtreleri vardır, fakat gerçek PostgreSQL cross-tenant
  testi Docker yokluğu nedeniyle skip edilmiştir. System-admin bypass database role
  Compose içinde provision edilmez.
- Login Keycloak hosted ekranındadır. Backend rate-limit filter login isteğini
  karşılamaz; login brute-force limiti Keycloak/edge sorumluluğudur.
- Performans, büyük dosya, uzun süreli SSE ve çoklu replica yük ölçümü yapılmadı.
- Repository'de Maven wrapper (`./mvnw`) yoktur; doğrulama sistem Maven 3.9.11 ile
  `mvn clean verify` kullanılarak yapıldı.

## Portal sınırları

- Dışarıdan erişilebilir backend/Keycloak URL'leri ve CORS/redirect ayarları
  verilmeden portal gerçek login/API akışına bağlanamaz.
- Portal SSE için bearer token destekli `fetch` stream kullanır; reconnect'te
  `Last-Event-ID`, kesintide polling fallback uygular. Gerçek proxy/browser üzerinden
  uzun bağlantı testi yapılmamıştır.
- Backend proje ve üye update API'leri tamdır. Portal ayarlar ekranı üye ekleme/çıkarma
  ve archive sunar; üye rol değiştirme ve bütün proje alanlarını update etme UI'ı
  henüz yoktur.
- Login formu portalda parola toplamıyor; güvenlik tasarımı gereği Keycloak hosted
  login ekranına yönlendiriyor.

## Audit sınırı

- Uygulama iş audit'i PostgreSQL `audit_event` tablosundadır.
- Login başarı/başarısızlık event'leri Keycloak'ta etkin ve loglanır; henüz
  uygulamanın append-only `audit_event` tablosuna kopyalanmaz.

## Sprint 4 Bilinen Konular

- `AI_MODEL_DEPLOYMENTS_JSON` varsayılan olarak boş olabilir. Gerçek extraction için
  en az bir lokal OpenAI-compatible runtime profile mapping'i yapılandırılmalıdır.
- Multi-instance SSE canlı yayın listesi process-local'dır. Kalıcı event replay güvenlidir;
  yatay ölçeklemede live fan-out için Redis/Rabbit stream adaptörü eklenmelidir.
- Semantic entailment, OCR alternate-text ve table-cell grounding extension noktaları
  tasarımda ayrıdır; mevcut çalışan validator exact/normalized/numeric/unit/page
  katmanlarını uygular.
- Clause signal motorunun aktif sağlayıcıları structure, approved terminology ve numeric
  sinyalleridir. Embedding ve düşük maliyetli semantic classifier sağlayıcısı henüz bağlı
  değildir.
- Onaylı müşteri evaluation dataset'i repository'ye konulmadı. Evaluation gate motoru
  testlidir fakat gerçek model baseline/candidate raporu lokal runtime ve müşteri verisi
  gerektirir.
- Bu ortamda Docker daemon olmadığı için V10 Flyway ve RLS Testcontainers testi
  çalıştırılamadı; SQL PostgreSQL 17 söz dizimine göre hazırlanmıştır.
- Standalone `tsc --noEmit`, mevcut Cloudflare worker ambient type paketleri tsconfig'e
  bağlı olmadığı için `cloudflare:workers`, `Fetcher` ve `D1Database` hataları verir.
  Vinext production build bu worker tiplerini kendi build ortamında başarıyla çözer.
