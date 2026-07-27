# Codex Handover

## 1. Yapılan işler

- Backend ve frontend net biçimde ayrılmış monorepo yapısı kuruldu.
- Java 21 / Spring Boot modüler monolith temeli tamamlandı.
- React tabanlı responsive ihale yönetim portalı oluşturuldu.
- PostgreSQL migration, MinIO, RabbitMQ, Redis ve Keycloak Compose altyapısı eklendi.
- JWT tenant claim'i ve realm rolleriyle API yetkilendirmesi kuruldu.
- İhale projesi CRUD dilimi ve PDF/DOCX yükleme akışı geliştirildi.
- Dosya binary'si MinIO'ya, metadata ve SHA-256 bilgisi PostgreSQL'e yazılıyor.
- RabbitMQ yayını transactional outbox üzerinden yapılıyor.
- Audit kayıtları veritabanı trigger'ı ile append-only hale getirildi.
- OpenContracts bağımlılığı provider-neutral port ve eşleme tablosu arkasında izole edildi.

## 2. Oluşturulan modüller

- `shared`: güvenlik ve API hata sözleşmeleri
- `identity`: rol sözleşmesi ve kullanıcı/rol tabloları
- `tender`: proje domain, application ve API katmanları
- `document`: doküman domain, MinIO storage ve REST API
- `audit`: değiştirilemez audit event modeli
- `integration/outbox`: RabbitMQ reliable publisher
- `document/integration`: OpenContracts/Docling gibi sağlayıcılar için bağımsız port

## 3. Çalışan ekranlar

- Responsive ana panel
- Proje portföyü
- Öncelikli projeler tablosu ve araması
- KPI, yaklaşan tarihler, görev ve aktivite panelleri
- Kullanıcı/rol, risk, rapor, ürün ve firma modülleri için navigasyon

Bu ekrandaki portföy verileri henüz backend'e bağlı değildir; gerçek veri bağlantısı sonraki
sprint işidir.

## 4. Çalışan API'ler

- `POST /api/v1/tenders`
- `GET /api/v1/tenders`
- `GET /api/v1/tenders/{id}`
- `PUT /api/v1/tenders/{id}`
- `POST /api/v1/tenders/{projectId}/documents`
- `GET /api/v1/tenders/{projectId}/documents`
- `GET /api/v1/documents/{documentId}/preview`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

## 5. Veritabanı tabloları

- `organization`
- `app_user`
- `user_role`
- `tender_project`
- `document`
- `document_version`
- `external_document_mapping`
- `audit_event`
- `outbox_event`

## 6. Test sonuçları

- Backend: 3 test, 0 failure, 0 error.
- Backend Java 21 production JAR build başarılı.
- Frontend production build başarılı.
- Git whitespace kontrolü başarılı.

## 7. Bilinen hatalar

- Mevcut makinede Docker daemon bulunmadığı için Compose servisleri birlikte ayağa
  kaldırılarak end-to-end doğrulanamadı.
- MinIO yazımı başarılı olup veritabanı transaction'ı sonradan başarısız olursa cleanup
  deneniyor; cleanup da başarısız olursa orphan object oluşabilir.
- Outbox publisher çoklu instance koşullarında `SKIP LOCKED` kullanmıyor.

## 8. Mock bırakılan alanlar

- Portal dashboard verileri statik örnek veridir.
- OpenContracts adapter implementasyonu yok; yalnız üretim sınırı tanımlı.
- Document worker ve madde çıkarımı henüz yok.
- Firma profili, ürün, risk, inceleme ve rapor navigasyonları placeholder davranışındadır.

## 9. Güvenlik eksikleri

- Dosya içerik magic-byte doğrulaması ve ClamAV taraması henüz yok.
- ZIP bomb ve şifreli PDF tespiti henüz yok.
- PostgreSQL RLS ikinci savunma katmanı henüz etkin değil.
- Rate limiting ve erişim audit'i henüz yok.
- Compose varsayılan parolaları yalnız yerel geliştirme içindir ve production'da
  secret store ile değiştirilmelidir.

## 10. Sonraki önerilen işler

1. Portalı Keycloak Authorization Code + PKCE akışına bağlamak.
2. Portal proje oluşturma ve yükleme ekranlarını gerçek API'ye bağlamak.
3. ClamAV ve magic-byte doğrulaması eklemek.
4. Document worker'ı ve durum event consumer'ını geliştirmek.
5. Clause tablosu, madde ağacı API'si ve PDF görüntüleyici ekranını tamamlamak.
6. PostgreSQL RLS ve Testcontainers izolasyon testlerini eklemek.
