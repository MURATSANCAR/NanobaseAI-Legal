# Backup Strategy

Kapsam: PostgreSQL, MinIO, Keycloak export, prompt/ontology/terminology/policy/workflow/report/model
registry ve deployment config. PostgreSQL full weekly + daily incremental/WAL; MinIO versioned
incremental; günlük realm/config export önerilir. Tenant recovery policy RPO/RTO ve retention’ı
V14 `recovery_policy` ile override eder.

`scripts/backup.sh` custom `pg_dump`, MinIO mirror, Keycloak export, SHA-256 manifest ve `age`
encryption üretir. Backup role ayrı, hedef explicit/offsite, bucket immutable retention ve key
rotation uygulanmalıdır.

Script çalıştırılmadı; backup mevcut sayılmaz.
