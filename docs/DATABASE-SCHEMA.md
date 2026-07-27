# Veritabanı Şeması

Şema Flyway `V1`–`V4` migration'larının birleşik son durumudur. Hibernate yalnız
doğrulama yapar (`ddl-auto=validate`).

## Ana tablolar

| Tablo | Amaç | Önemli ilişkiler/kısıtlar |
|---|---|---|
| `organization` | Tenant kökü | UUID PK |
| `app_user` | Harici identity eşlemesi | organization FK; subject ve email organization içinde unique |
| `user_role` | Sistem rolleri | user FK; `(user_id, role)` PK |
| `tender_project` | İhale projesi | organization FK; `(organization_id, project_code)` unique; optimistic `version` |
| `project_member` | Proje üyeliği ve izinleri | project + organization; `(project_id, user_id)` unique |
| `document` | Mantıksal doküman | organization + project; `current_version_id`; optimistic `version` |
| `document_version` | Immutable dosya revizyonu metadata'sı | document + organization; `(document_id, version_number)` unique; object key unique |
| `clause` | Ayrıştırılan madde sınırı | document version FK; version içinde clause number unique |
| `external_document_mapping` | Dış sağlayıcı eşlemesi | document version + organization; provider/sync status check |
| `outbox_event` | Güvenilir domain event yayını | aggregate, envelope, status/retry/next attempt alanları |
| `processed_event` | Consumer idempotency | `event_id` PK |
| `audit_event` | Değiştirilemez aktivite kaydı | organization scope; DB trigger update/delete'i reddeder |

## Tender ve member

`tender_project` alanları: `id`, `organization_id`, `project_code`, `name`,
`institution_name`, `tender_registration_number`, `tender_type`, `business_type`,
`sector`, `priority`, `status`, `bid_deadline`, `clarification_deadline`,
`description`, `currency`, `owner_user_id`, `created_by`, `created_at`, `updated_at`,
`version`.

Tarih check constraint'i clarification tarihinin bid deadline'dan sonra olmasını
engeller. Kodlar `tender_project_code_seq` sequence'inden üretilir.

`project_member` alanları: `id`, `organization_id`, `project_id`, `user_id`,
`project_role`, dört boolean proje izni ve `created_at`.

## Document ve version

`document`: `id`, `organization_id`, `project_id`, `logical_name`, `document_type`,
`status`, `current_version_number`, `current_version_id`, `included_in_analysis`,
`created_by`, timestamp'ler ve `version`.

`document_version`: `id`, `organization_id`, `document_id`, `version_number`,
`object_storage_key`, `original_file_name`, `mime_type`, `file_size`, `sha256`,
`page_count`, `language`, `ocr_required`, `ocr_quality_score`, `processing_status`,
`uploaded_by`, processing/upload timestamp'leri, hata alanları ve `version`.

Binary veri bu şemada tutulmaz.

## Event tabloları

`outbox_event`: `id`, `aggregate_type`, `aggregate_id`, `event_type`,
`event_version`, `routing_key`, `payload_json`, `organization_id`, `correlation_id`,
`status`, `retry_count`, `next_attempt_at`, `created_at`, `published_at`,
`last_error`.

`processed_event`: işlenen event ID'sini organization, tip ve zamanla saklar.

`audit_event`: `id`, `organization_id`, `user_id`, `event_type`, `entity_type`,
`entity_id`, `ip_address`, `user_agent`, `before_json`, `after_json`,
`correlation_id`, `created_at`.

## İndeksler

- `tender_project`: organization/status, organization/bid deadline,
  organization/project code.
- `project_member`: project/user ve organization/user.
- `document`: project/status ve organization/project.
- `document_version`: document/version number, organization/status ve SHA-256.
- `external_document_mapping`: version/provider.
- `outbox_event`: status/next attempt.
- `audit_event`: organization/created time ve entity type/entity ID.

## Migration notu

Önceki `V1`–`V3` migration'ları değiştirilmedi. `V4__complete_platform_mvp.sql`
mevcut kolonları rename eder, yeni alan/kısıt/indeksleri ekler ve legacy, publish
edilmemiş outbox payload'larını uyumsuz envelope yaymamak için `FAILED` durumuna taşır.

