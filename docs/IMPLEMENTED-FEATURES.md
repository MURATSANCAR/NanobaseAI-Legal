# Uygulanan Özellikler

## Platform temeli

- Java 21 ve Spring Boot 3.4 tabanlı modüler monolith backend.
- Next.js/React/TypeScript tabanlı responsive portal.
- PostgreSQL, MinIO, RabbitMQ, Redis ve Keycloak için sabit sürümlü Compose servisleri.
- Yerel seed kullanıcı parolasını repository dışında tutan idempotent Keycloak init adımı.
- Flyway migration zinciri ve Hibernate `ddl-auto=validate`.
- JWT resource server, Keycloak Authorization Code + PKCE ve realm rol eşlemesi.
- JWT içindeki `tenant_id` claim'inden türetilen organization kapsamı.
- RFC 7807 hata sözleşmesi, correlation ID ve alan bazlı validasyon hataları.
- Liveness, readiness ve Prometheus endpoint'leri.

## İhale projeleri

- Proje oluşturma, sayfalı listeleme, detay, güncelleme ve arşivleme.
- `TND-{YEAR}-{SEQUENCE}` biçiminde veritabanı sequence'i ile üretilen proje kodu.
- Organization + proje kodu unique constraint'i.
- Optimistic locking ve tarih kuralları.
- Proje sahibinin otomatik `OWNER` üyesi olması.
- Üye listeleme, ekleme, yetki/rol güncelleme ve çıkarma.
- Organization ve proje üyeliği kapsamlı erişim sorguları.

## Doküman yönetimi

- PDF ve DOCX yükleme.
- Mantıksal `Document` ile fiziksel `DocumentVersion` ayrımı.
- Dosya adı sanitization, Apache Tika ile içerik tabanlı MIME kontrolü, boyut sınırı ve
  SHA-256 üretimi.
- Aynı proje içindeki aynı hash için duplicate kontrolü.
- Binary içeriğin private MinIO bucket'ına yazılması; veritabanında yalnız metadata.
- Yeni versiyon oluşturma, monoton versiyon numarası, eski versiyonların korunması.
- Kısa süreli, varsayılan 300 saniyelik presigned indirme URL'si.
- Doküman detay, liste, versiyon, clause, yeniden işleme ve SSE endpoint'leri.
- Pessimistic lock ile aynı dokümana eşzamanlı versiyon/reprocess işlemlerinin
  serileştirilmesi.

## Asenkron işleme

- Veritabanı işlemiyle aynı transaction'da oluşturulan transactional outbox kaydı.
- RabbitMQ publisher confirm sonrasında `PUBLISHED` işaretleme.
- Batch claim sırasında `FOR UPDATE SKIP LOCKED`.
- `specai.events` exchange'i, `document.uploaded.v1` routing key'i ve
  `document-processing` kuyruğu.
- 30 saniye, 2 dakika ve 10 dakikalık sonlu retry kuyrukları ve DLQ.
- `processed_event` tablosu ile consumer idempotency.
- Doküman durum makinesi ve audit/SSE ilerleme yayını.
- Provider-neutral `DocumentIntelligencePort`.
- Entegrasyon kapalıyken sahte veri veya `READY` üretmeyen, açıkça
  `MANUAL_REVIEW_REQUIRED` döndüren disabled adapter.
- Yapılandırma ile açılan OpenContracts HTTP adapter sınırı.

## Audit ve observability

- Proje, üye, doküman, versiyon, indirme URL'si, yeniden işleme ve status değişimleri
  için organization kapsamlı audit kayıtları.
- PostgreSQL trigger'ı ile `audit_event` update/delete engeli.
- Gelen `X-Correlation-ID` değerini koruyan veya UUID üreten request filtresi.
- Log context'inde correlation ID, organization ve kullanıcı.
- Doküman/outbox/consumer metrikleri ve Prometheus registry.
- PostgreSQL, RabbitMQ, Redis, MinIO ve document-intelligence readiness bileşenleri.

## Portal

- Keycloak'a yönlendiren korumalı login akışı ve PKCE callback işleme.
- API verisinden dashboard metrikleri, yaklaşan tarihler ve son aktiviteler.
- Proje listesi ve durum/kurum/sorumlu/tarih filtreleri.
- Dört adımlı yeni proje wizard'ı.
- Genel bakış, dokümanlar, aktivite ve ayarlar sekmeli proje detayı.
- Doküman yükleme, status polling, hata detayı, reprocess, indirme, versiyon listesi ve
  yeni versiyon yükleme.
- Üye ekleme/çıkarma ve proje arşivleme.
- Loading, empty, error, correlation ID ve retry durumları.
