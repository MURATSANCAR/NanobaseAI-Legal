# Quality Debt

Quality debt teklif, açık kabul ve telafi kontrolleriyle yönetilir. Varsayılan durum
`PROPOSED`’dır. Kabul için tenant/system admin, en az bir compensating control ve audit
kaydı gerekir.

`CRITICAL` severity ile `AUTHORIZATION`, `SECURITY_SCAN` veya altyapısal güvenlik/data
loss kök nedenleri ertelenemez. Bu kural uygulama servisinde fail-closed uygulanır.

API:

- `POST /api/v1/quality-debt`
- `POST /api/v1/quality-debt/{id}/accept`

Bu teslimde kabul edilmiş gerçek quality debt yoktur.
