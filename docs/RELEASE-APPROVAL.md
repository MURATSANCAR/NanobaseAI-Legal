# Release Approval

Approval sırası kodda değildir. `RELEASE_APPROVAL_DEFAULT` policy v1 başlangıçta
Technical Lead, Security, Product, Operations ve Customer Acceptance adımlarını ister.
Tenant policy yeni sürümle bu zinciri değiştirebilir.

Approval ancak manifest, zorunlu gate’ler ve blocker kontrolü uygunsa başlar. Her adım
actor, zaman, karar ve comment taşır. Bütün adımlar APPROVED olmadan release approved
olmaz. Bir REJECTED adımı release’i reddeder.

API:

- `POST /api/v1/releases/{id}/approve`
- `POST /api/v1/release-approvals/{id}/decisions`
