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

Portal proje, doküman, yükleme, durum, preview ve madde API'lerine bağlıdır.

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

- OpenContracts adapter implementasyonu yok; yalnız üretim sınırı tanımlı.
- OCR ve karmaşık tablo parsing'i henüz yok.
- Firma profili, ürün, risk, inceleme ve rapor navigasyonları placeholder davranışındadır.

## 9. Güvenlik eksikleri

- Tika içerik tespiti ve ClamAV taraması vardır; parser sandbox ve ZIP bomb koruması yoktur.
- PostgreSQL RLS ikinci savunma katmanı henüz etkin değil.
- Rate limiting ve erişim audit'i henüz yok.
- Compose varsayılan parolaları yalnız yerel geliştirme içindir ve production'da
  secret store ile değiştirilmelidir.

## 10. Sonraki önerilen işler

1. OpenContracts adapter implementasyonu ve contract testleri.
2. OCR/Docling ve parser routing.
3. SSE canlı işlem ilerlemesi.
4. PostgreSQL RLS ve Testcontainers izolasyon testleri.
