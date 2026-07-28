# Sprint 4 Test Sonuçları

Son doğrulama tarihi: 2026-07-28.

## Otomatik kontroller

- Backend unit + architecture: `mvn verify` — **49 başarılı**
- Backend temiz derleme: `mvn clean test -DskipTests`
- Frontend lint: `pnpm lint` — **başarılı**
- Frontend production build + contract test: `pnpm test` — **8 başarılı**
- AI Orchestrator syntax: `python -m compileall -q services/ai-orchestrator`
- AI Orchestrator FastAPI contract: **3 başarılı** (live, ready, deployment yoksa 503)

Sprint 4 motor testleri clause policy aggregation, confidence factors, exact/normalized/
numeric grounding, dynamic output schema, dynamic strategy/model profile, duplicate
thresholds ve extensible evaluation quality gate'lerini kapsar.

Architecture testleri domain-infrastructure ayrımına ek olarak fixed taxonomy/runtime
bağımlılığını, controller routing yasağını ve analysis policy portlarını doğrular.

Docker daemon bu çalışma ortamında bulunmadığından 5 Testcontainers/Flyway integration
testi `disabledWithoutDocker` ile skip edildi. Suite V10 tablo varlığını, requirement
queue'yu ve ontology/terminology tenant izolasyonunu Docker bulunan CI'da doğrulayacak
şekilde genişletildi.

Ek `tsc --noEmit` denemesi mevcut Cloudflare worker ambient type'ları
(`cloudflare:workers`, `Fetcher`, `D1Database`) tsconfig kapsamında bulunmadığı için
başarısızdır. Uygulamanın gerçek Vinext production build'i ve testleri başarılıdır.
