# Backend Down

**Belirti:** readiness down, 5xx ve frontend API hatası. **Neden:** process crash, config/TLS,
DB/broker dependency veya rollout. **Kontrol:** orchestrator event/log, liveness/readiness,
release digest ve dependency health; secret değerini yazdırma. **Müdahale:** trafiği healthy
replica’ya al, failed rollout’u önceki immutable image’a döndür, dependency runbook’una geç.
**Geri alma:** aynı config ile önceki image. **Veri riski:** in-flight transaction rollback;
outbox/job state doğrulanmalı. **Eskalasyon:** 5 dk SRE, 15 dk platform owner/P1.
