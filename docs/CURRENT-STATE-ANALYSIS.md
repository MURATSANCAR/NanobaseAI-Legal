# Mevcut Durum Analizi

Bu belge 27 Temmuz 2026 tarihinde repository'deki kaynak kod, migration'lar,
container tanımları, testler ve teslim dokümanları doğrudan incelenerek
hazırlanmıştır. Henüz uygulanmamış veya uçtan uca doğrulanmamış bir alan
“çalışıyor” olarak sınıflandırılmamıştır.

## 1. Mevcut mimari

- Repository kökünde Java 21 / Spring Boot 3.4.7 tabanlı tek uygulama ve
  `frontend/` altında React 19, TypeScript ve vinext tabanlı portal bulunuyor.
- Backend paketleri `tender`, `document`, `audit`, `identity`, `organization`,
  `integration/outbox` ve `shared` olarak ayrılmış. Domain/application/api
  ayrımı kısmen uygulanmış; tüm modüllerde tam ve tutarlı değil.
- Kalıcı veri PostgreSQL ve Flyway, dosya depolama MinIO, mesajlaşma RabbitMQ,
  cache bağlantısı Redis üzerinden tasarlanmış.
- Kimlik doğrulama uygulama içi parola tablosu yerine Keycloak OIDC ve JWT
  resource server ile sağlanıyor. `tenant_id` doğrulanmış JWT'den okunuyor.
- Doküman metadata'sı PostgreSQL'e, binary içerik MinIO'ya yazılıyor.
- RabbitMQ yayını için temel bir outbox tablosu ve periyodik publisher var.
- Python document worker RabbitMQ'dan mesaj alıp MinIO'dan dosya indiriyor,
  ClamAV taraması ve PDF/DOCX metin çıkarımı yaparak internal HTTP endpoint'ine
  sonuç gönderiyor.

## 2. Çalışan modüller

- Spring Security JWT doğrulaması, realm role dönüşümü ve tenant claim okuması.
- Tenant filtreli proje oluşturma, listeleme, görüntüleme ve güncelleme servisleri.
- PDF/DOCX upload için multipart endpoint, Tika içerik tipi kontrolü, SHA-256
  hesabı ve MinIO yazma adaptörü.
- `Document` ve `DocumentVersion` için temel JPA modelleri.
- Signed MinIO URL üretimi; mevcut endpoint `/preview`, süre 10 dakika.
- Temel append-only audit trigger'ı.
- Temel outbox publisher ve durable RabbitMQ exchange/queue tanımları.
- Actuator health/liveness/readiness endpoint yapılandırması.
- Responsive fakat statik tek sayfalı dashboard görünümü.

Bu maddeler kaynak kod seviyesinde mevcuttur. Başlangıç ortamında Java, Maven
ve Docker PATH üzerinde bulunmadığından bu analiz aşamasında backend veya
container çalışma zamanı doğrulaması yapılamamıştır.

## 3. Eksik modüller

- Tender arşivleme ve project member ekleme/çıkarma/rol değiştirme akışları.
- Şartnamedeki tüm TenderProject alanları, tarih doğrulamaları ve organization
  bazlı atomik `TND-{YEAR}-{SEQUENCE}` kod üretimi.
- Proje üyeliğine bağlı erişim ve işlem yetkileri.
- Doküman detay, versiyon listeleme/yükleme, reprocess, 5 dakikalık download URL
  ve SSE processing events endpoint'leri.
- `Document` ve `DocumentVersion` şartname alanlarının büyük bölümü.
- Duplicate dosya kontrolü, current version FK'si ve versiyon numarasını
  eşzamanlı artırma.
- Şartnamedeki tam event envelope, outbox durum/retry alanları ve kontrollü
  retry/DLQ zinciri.
- Consumer idempotency kaydı.
- Provider-neutral OpenContracts ve disabled adapter implementasyonları.
- External mapping lifecycle alanları ve repository/application katmanı.
- Audit listeleme API'si, tam audit alanları ve tanımlı event adlarının tümü.
- Request correlation ID filtresi ve standart `code`, `correlationId`,
  `fieldErrors` içeren Problem Details cevabı.
- Özel MinIO readiness göstergesi ve Prometheus iş metrikleri.
- Gerçek backend verisiyle çalışan login, dashboard, proje, üye ve doküman
  ekranları.
- Testcontainers tabanlı PostgreSQL/RabbitMQ/MinIO/Redis entegrasyon testleri.

## 4. Hatalı veya riskli tasarımlar

- Proje kodu UUID parçasından üretiliyor; istenen sıralı formatı ve concurrency
  garantisini sağlamıyor. `code` unique kısıtı organization yerine global.
- `Document` mevcut versiyonu FK ile değil tamsayıyla tutuyor.
- Upload object key'i dosya adını içermiyor ve şartname formatıyla uyuşmuyor.
- Upload önce MinIO'ya, sonra transaction içindeki kayıtları yazıyor. Cleanup
  denemesi olsa da process crash veya cleanup hatasında orphan object kalabilir.
- Dosya `Content-Type` header'ı geçersizse, içerik Tika tarafından doğru
  bulunsa bile istek reddediliyor. Güvenlik kararı istemci header'ına bağlı.
- Dosya adındaki yalnız path ve CR/LF temizleniyor; kontrol karakterleri,
  ayrılmış adlar ve aşırı uzun adlar güvenli biçimde normalize edilmiyor.
- Duplicate hash kontrolü yok.
- Outbox publisher broker confirm sonucunu beklemiyor, satırları kilitlemiyor,
  durum/next-attempt alanı tutmuyor ve mesajı transaction açıkken gönderiyor.
- RabbitMQ queue/routing/DLQ adları şartnameyle uyuşmuyor; gecikmeli 30 saniye,
  2 dakika, 10 dakika retry zinciri yok.
- Python worker başarısız mesajı doğrudan DLQ'ya gönderiyor; retry yok.
- Worker gerçek Document Intelligence port'una bağlı değil ve basit regex
  sonucu ile dokümanı `READY` yapıyor. Bu, gerçek entegrasyon yokken sahte
  sonuç üretmeme kuralına aykırı.
- Worker internal endpoint'inin Spring Security'de genel `permitAll` olması,
  controller içi paylaşılan secret kontrolüne tek savunma olarak güveniyor.
- Processing callback `tenantId` değerini worker body’sinden kabul ediyor.
- Processing status geçiş modeli serbest; terminal durumdan geriye veya
  geçersiz sıraya geçiş engellenmiyor. `FAILED` enum değeri yok.
- Normal write rollerinde `TECHNICAL_REVIEWER` tüm POST/PUT isteklerine
  erişebiliyor; şartnamedeki minimum yetkilerle uyuşmuyor.
- Audit modeli gerekli IP, user-agent, before/after ve correlation alanlarını
  taşımıyor; event adları şartnameyle uyuşmuyor.
- Mevcut README ve handover, gerçekte olmayan veya eksik kalan frontend/worker
  yeteneklerini kısmen tamamlanmış gibi gösteriyor.

## 5. Mock bırakılmış alanlar

- Dashboard KPI'ları, projeler, son tarihler, görevler ve aktiviteler sabit
  TypeScript dizilerinden geliyor.
- Navigasyon butonları yalnız toast üretiyor; route veya gerçek işlem yok.
- Kullanıcı/firma/rol bilgileri sabit metin.
- `DocumentIntelligencePort` yalnız interface; üretim adapter'ı ve açıkça
  disabled adapter yok.
- Python worker'ın regex ile clause üretmesi gerçek document intelligence
  yerine placeholder davranışı gösteriyor ve production akışında aktif.

## 6. Veritabanı durumu

- Üç migration var: platform temeli, identity/document/outbox ve clause.
- `organization`, `app_user`, `user_role`, `tender_project`, `document`,
  `document_version`, `external_document_mapping`, `outbox_event`,
  `audit_event`, `clause` tabloları tanımlı.
- `spring.jpa.hibernate.ddl-auto=validate` doğru ayarlanmış.
- Şartnamedeki `project_member` ve proje kod sayacı yok.
- Mevcut kolon adları sıkça şartnameden farklı (`tenant_id`, `code`,
  `contracting_authority`, `current_version`, `object_key`, `media_type`).
- Gerekli FK, check constraint, organization-scope unique constraint ve
  indekslerin bir bölümü eksik.
- Mevcut migration'ları değiştirmek kurulu veritabanlarını bozacağından
  düzeltmeler yeni migration numaralarıyla yapılmalıdır.

## 7. Docker altyapısı

- Compose PostgreSQL, MinIO, RabbitMQ, Redis, Keycloak, backend, frontend,
  ClamAV ve document worker tanımlıyor.
- Ana servislerde named volume, healthcheck ve restart policy büyük ölçüde var.
- Özel network tanımlı değil; Compose default network kullanılıyor.
- MinIO init yalnız `specai-original` bucket'ını oluşturuyor; diğer dört bucket
  yok.
- `document-worker` profile arkasında değil ve disabled document intelligence
  davranışı bulunmuyor.
- Backend container healthcheck tanımlı değil; worker `service_started`
  koşuluyla başlayabiliyor.
- `.env.example` yok. Compose'daki development fallback secret'ları açıkça
  local amaçlı olsa da production için zorunlu değişim kontrolü bulunmuyor.
- Docker CLI bulunmadığı için `docker compose config/up/ps` henüz
  doğrulanamadı.

## 8. Test durumu

- İki Mockito unit test sınıfı var; proje oluşturma/tenant lookup ve temel
  document upload yan etkilerini test ediyor.
- Proje kodu concurrency, tarih kuralları, sanitization, SHA, versiyon,
  authorization ve status geçişleri için kapsamlı unit test yok.
- Testcontainers bağımlılığında yalnız PostgreSQL modülü var; RabbitMQ, MinIO
  ve Redis modülleri/testleri yok.
- HTTP tenant kaçışı, signed URL, outbox publish, consumer idempotency/retry/DLQ
  entegrasyon testleri yok.
- Frontend `test` script'i repository'de bulunmayan
  `tests/rendered-html.test.mjs` dosyasını çalıştırıyor.
- Başlangıç komutları ortamda Java/Maven/npm/Docker bulunmadığı için
  çalıştırılamadı. Paketli Node çalışma zamanı daha sonraki frontend doğrulaması
  için kullanılabilir.

## 9. Bu görevde uygulanacak değişiklikler

- Mevcut V1–V3 migration'larını koruyup yeni migration'larla şemayı şartnameye
  uyarlamak.
- Atomik organization/yıl bazlı proje kodu, tam proje alanları, archive ve
  project member yönetimini eklemek.
- Rol ve proje üyeliği tabanlı application-level yetkilendirme ile bütün
  repository sorgularını tenant kapsamlı tutmak.
- Doküman/current version modelini tamamlamak; duplicate kontrolü, yeni
  versiyon, reprocess, detay/list/download URL ve sınırlı SSE akışını eklemek.
- Event envelope ve güvenli outbox state/retry modelini, RabbitMQ retry/DLQ
  topolojisini ve consumer idempotency davranışını eklemek.
- Sahte `READY` üretimini kaldırıp açık feature flag ile çalışan
  `DisabledDocumentIntelligenceAdapter` ve dış entegrasyon port sınırı kurmak.
- Audit model/API'sini ve correlation-aware RFC 7807 hata sözleşmesini
  tamamlamak.
- Dashboard, login, proje listesi/wizard/detay ve doküman merkezi ekranlarını
  gerçek API sözleşmeleriyle bağlamak; loading/empty/error ve polling fallback
  durumlarını eklemek.
- Compose bucket/profile/health/network/secrets ayarlarını, `.env.example`,
  metrik ve health altyapısını tamamlamak.
- Uygulanabilir unit/integration/frontend testlerini eklemek; mevcut ortamın
  izin verdiği bütün build/test/config komutlarını gerçek olarak çalıştırmak.
- Teslim dokümanlarını son doğrulama sonuçları ve açık kalan sınırlamalarla
  güncellemek.
