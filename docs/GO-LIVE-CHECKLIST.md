# Go-live Checklist

| Kategori | Sorumlu | Kanıt | Durum | Blocker |
|---|---|---|---|---|
| Security/pentest | Security | Security results + report | NOT_VERIFIED | CRITICAL |
| Tenant isolation/RLS | Platform/DBA | Real integration run | NOT_VERIFIED | CRITICAL |
| Performance/large docs | SRE/AI | k6 + corpus metrics | NOT_VERIFIED | HIGH |
| Availability/chaos | SRE | Recovery report | NOT_VERIFIED | HIGH |
| Backup/restore/RPO-RTO | DBA/SRE | Encrypted backup + restore sentinel | NOT_VERIFIED | CRITICAL |
| Monitoring/alerting | SRE | Dashboard + fired alert evidence | PARTIAL | HIGH |
| Runbooks/support | SRE/Support | 13 runbook + on-call drill | PARTIAL | MEDIUM |
| UAT/training | Customer/Product | Signed UAT | NOT_VERIFIED | CRITICAL |
| License | Legal | Exact SBOM/model/image review | NOT_VERIFIED | CRITICAL |
| Offline install | Release | Air-gap install report | NOT_VERIFIED | HIGH |
| Rollback | Release/SRE | Tested rollback | NOT_VERIFIED | HIGH |
| Customer sign-off | Customer | Signed acceptance | NOT_VERIFIED | CRITICAL |

Production recommendation: **NO-GO** until every CRITICAL blocker has dated evidence and owner.
