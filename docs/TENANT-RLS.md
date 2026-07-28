# Tenant RLS

V8 aşağıdaki tablolarda RLS ve `FORCE ROW LEVEL SECURITY` etkinleştirir:

`tender_project`, `document`, `document_version`, `external_document_mapping`,
`audit_event`, `document_processing_job`, `processing_event`, `document_page`,
`clause`, `document_table`, `parser_warning`.

Policy:

```sql
organization_id = app_current_organization_id()
```

`TenantTransactionFilter`, doğrulanmış JWT claim’ini request transaction’ında
`set_config('app.current_organization_id', id, true)` ile set eder. Worker/application
servisleri background transaction’larında `TenantDatabaseContext` kullanır.

Repository organization filtreleri korunmuştur ve RLS yerine geçmez. System admin
bypass role production DBA tarafından ayrı, login olmayan bir database role olarak
tanımlanmalıdır; uygulama rolüne genel `BYPASSRLS` verilmemelidir.

