# Data Retention

V14 tenant `retention_policy` resource code/concept, interval, archive/delete JSON, legal hold ve
active state taşır. Document, processed text, model metadata, audit, report, backup, temp,
failed job, notification ve feedback ayrı policy olmalıdır.

Deletion yetkili command, project/tenant scope, legal hold pre-check, immutable audit ve backup
etkisi açıklaması gerektirir. Temp/orphan lifecycle otomatik; domain/audit/report deletion
asenkron job ve two-person approval kullanmalıdır.

Schema vardır; policy resolver/deletion worker ve backup tombstone integration testi yoktur.
