# Database Security

V8–V14 tenant tablolarında RLS ve `FORCE ROW LEVEL SECURITY` vardır. Transaction-local
`app.current_organization_id`, doğrulanmış JWT tenant’ından set edilir. Repository sorguları
ek olarak organization/project predicate kullanır. `pgcrypto`, V14 audit SHA-256 için izinli
tek yeni extension’dır.

Production: non-superuser application role, ayrı migration ve backup role; `public` create
revoke; TLS `sslmode=verify-full`; statement/idle transaction timeout; sınırlı Hikari pool;
slow query log ve read-only reporting role deployment SQL’inde uygulanmalıdır.

`PlatformInfrastructureIT#rlsBlocksCrossTenantSqlAccess` gerçek Postgres Testcontainers testidir,
ancak Docker olmadığı için bu çalışmada atlandı. Bu nedenle database security
`PARTIALLY_VERIFIED` durumundadır.
