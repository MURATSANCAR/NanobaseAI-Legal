# Implemented Features

## Platform

- Java 21 ve Spring Boot 3.4 tabanlı modüler monolith
- React 19 / Next-compatible portal
- PostgreSQL 17 ve versiyonlu Flyway migration'ları
- Keycloak OIDC resource server entegrasyonu
- MinIO object storage
- RabbitMQ durable queue, dead-letter queue ve transactional outbox
- Redis bağlantı altyapısı
- Container healthcheck ve non-root application image

## Tender

- Tenant kapsamında proje oluşturma
- Sayfalı proje listeleme
- Proje görüntüleme ve güncelleme
- Optimistic locking
- Request validation ve standart Problem Details cevapları

## Document

- PDF ve DOCX multipart upload
- Maksimum istek boyutu yapılandırması
- Güvenli dosya adı normalizasyonu
- SHA-256 hash üretimi
- Tenant/project/document/version temelli object key
- MinIO signed preview URL
- Doküman durum modeli
- `DocumentUploaded` outbox olayı

## Security and Audit

- Doğrulanmış JWT'den zorunlu `tenant_id`
- Realm role tabanlı endpoint yetkilendirmesi
- İstemci body/header üzerinden tenant kabul etmeme
- Tenant filtreli repository sorguları
- Append-only audit trigger'ı
- Audit actor, tenant, aggregate, zaman ve payload kayıtları
