# Mimari Kararlar

## ADR-001 — Modüler monolith

Backend tek deploy edilebilir Spring Boot uygulaması olarak tutuldu. `tender`, `document`,
`audit`, `organization`, `identity`, `integration/outbox` ve `shared` paketleri iş
sınırlarını belirliyor. Controller'lar API modelini application servislerine aktarır;
entity'ler doğrudan response olarak dönmez.

Bu seçim MVP operasyonunu basit tutarken document intelligence gibi ağır işlerin
sonradan ayrı worker/service olarak çıkarılmasına izin verir.

## ADR-002 — Tenant yalnız doğrulanmış JWT'den alınır

Organization kimliği request body veya özel tenant header'ından kabul edilmez.
`JwtCurrentTenant`, doğrulanmış access token içindeki `tenant_id` claim'ini zorunlu
UUID olarak okur. Repository sorguları `organization_id` ile scope edilir. Proje üyeliği
ikinci bir yetki katmanıdır.

PostgreSQL RLS henüz kullanılmadığı için bu izolasyon application/repository katmanına
dayanır. RLS, sonraki sertleştirme adımıdır.

## ADR-003 — Keycloak ve PKCE

Parola ve refresh token yönetimi uygulamaya kopyalanmadı. Portal public OIDC client
olarak Authorization Code + PKCE S256 kullanır; backend stateless resource server'dır.
Login başarı/başarısızlık event'lerini Keycloak üretir ve kendi event listener/store
mekanizmasında tutar.

## ADR-004 — Logical document ve immutable version

`document` kullanıcı açısından mantıksal kaydı, `document_version` her binary revizyonu
temsil eder. `current_version_id` aktif revizyonu gösterir. Önceki versiyonlar silinmez.
Dosya MinIO'da, metadata PostgreSQL'de saklanır.

## ADR-005 — Transactional outbox ve at-least-once teslim

Doküman metadata'sı ile `DocumentUploaded` envelope'u aynı PostgreSQL transaction'ında
yazılır. Ayrı scheduler mesajı publish eder ve broker confirm alır. Bu model
at-least-once'dur; consumer tekrar teslimleri `processed_event` ile idempotent işler.
Sequence ve outbox kayıtlarında boşluk oluşabilmesi normaldir.

## ADR-006 — Provider-neutral document intelligence

Domain dış sağlayıcı tiplerini bilmez. `DocumentIntelligencePort` arkasında
OpenContracts HTTP adapter'ı ve disabled adapter vardır. Entegrasyon kapalıyken sistem
`MANUAL_REVIEW_REQUIRED` üretir; sahte clause/page veya `READY` üretmez.

## ADR-007 — Durum makinesi ve ilerleme

Doküman durumu izinli geçişlerle kontrol edilir. Worker her değişimde hem ana
`Document` hem güncel `DocumentVersion` durumunu günceller, audit yazar ve sınırlı
ömürlü SSE event'i yayımlar. Portalın güvenilir fallback'i periyodik polling'dir.

## ADR-008 — Güvenli dosya erişimi

MinIO bucket'ları private'dır. Object key organization/proje/doküman/versiyon
hiyerarşisini ve sanitize edilmiş adı içerir. API object key'i istemciye açıklamaz;
indirme için yetki kontrolünden sonra 5 dakikalık presigned URL verir.

## ADR-009 — Project code

Kod üretimi `tender_project_code_seq` PostgreSQL sequence'ini kullanır. Format
`TND-{UTC yılı}-{6 hane}` şeklindedir ve `(organization_id, project_code)` unique
constraint'i son savunmadır. Sequence global ve monoton artar; organization başına
kesintisiz sayaç garantisi vermez.

## ADR-010 — Kalıcı veri ve geliştirme altyapısı

Compose servisleri named volume kullanır, host portlarını yalnız loopback'e bağlar ve
health/dependency koşulları tanımlar. Secret değerleri Compose dosyasına gömülmez;
`.env.example` gerçek değer olmadan sözleşmeyi gösterir.

