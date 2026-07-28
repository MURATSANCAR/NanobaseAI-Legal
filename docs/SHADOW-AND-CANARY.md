# Shadow and Canary

`shadow_execution` aktif ve aday snapshot’ı ayrı tutar; candidate JSON kullanıcıya/production
domain state’e yazılmaz, comparison ve expert feedback’e gider. `canary_assignment` tenant,
project, user group ve traffic percentage scope’u ile explicit rollback snapshot taşır.

Rollout: approved gate → shadow → expert review → bounded canary → metric/error guard → activate
veya rollback. Hash-based stable cohort seçimi uygulanmalı; bir kullanıcı/job aynı cohort’ta
kalmalıdır.

Şema/UI göstergesi vardır; execution router, metric guard ve rollback integration testi yoktur.
Durum `PARTIALLY_VERIFIED`.
