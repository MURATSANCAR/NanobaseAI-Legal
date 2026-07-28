# Production Rollout

Rollout strategy versioned policy/deployment profile’dan gelir. Varsayılan
`TENANT_BY_TENANT`; desteklenen modeller big bang, blue-green, canary,
tenant-by-tenant, project-by-project ve offline manual’dır.

Her aşamada health, error rate, latency, queue, model/parser availability, tenant
authorization, upload, extraction, reporting, audit, backup ve security alert kontrol
edilir. `stopOnFailure=true` varsayılandır.

İlk üretim için müşteri altyapısına göre tenant-by-tenant veya blue-green + sınırlı
canary seçilmelidir. Bu seçim runtime profile olmadan yapılmamıştır.
