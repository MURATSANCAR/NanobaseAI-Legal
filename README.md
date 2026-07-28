# NANObaseAI Şartname AI

Şartname AI; ihale projeleri ile PDF/DOCX dokümanlarını organization kapsamında
yöneten, dosyaları MinIO'da saklayan ve işlemeyi transactional outbox üzerinden
RabbitMQ'ya aktaran on-premise MVP'dir.

## Hızlı başlangıç

Ön koşul: Docker Engine ve Docker Compose v2.

```bash
cp .env.example .env
# .env içindeki bütün örnek secret'ları değiştirin
docker compose config
docker compose up --build -d
docker compose ps
```

- Portal: `http://localhost:3000`
- API readiness: `http://localhost:8080/actuator/health/readiness`

Yerel seed kullanıcı: `admin@nanobase.local`. Parola `.env` içindeki
`SPECAI_LOCAL_ADMIN_PASSWORD` (veya `LOCAL_USER_PASSWORD`) değeridir.
Geçici auth: local HS256 JWT (`SPECAI_AUTH_MODE=local`); Keycloak yoktur.

## Geliştirici testleri

```bash
mvn clean verify
cd frontend
pnpm install --frozen-lockfile
pnpm run build
pnpm run test
```

Java 21 ve Node.js 22.13+ gerekir. Testcontainers entegrasyon testleri için çalışan
Docker daemon zorunludur.

## Mimari özet

Backend Java 21/Spring Boot modüler monolith, portal Next.js/React/TypeScript'tir.
PostgreSQL kalıcı metadata ve outbox, MinIO private object storage, RabbitMQ
at-least-once event teslimi, Redis altyapı bağımlılığı ve Keycloak OIDC/PKCE sağlar.

Organization istemciden alınmaz; doğrulanmış JWT `tenant_id` claim'inden türetilir.
Kimlik doğrulama varsayılan olarak uygulama içi local JWT login ile sağlanır.
Gerçek document-intelligence entegrasyonu varsayılan olarak kapalıdır ve kapalıyken
dokümanlar sahte `READY` yerine `MANUAL_REVIEW_REQUIRED` olur.

## Dokümantasyon

- [Mevcut durum analizi](docs/CURRENT-STATE-ANALYSIS.md)
- [Uygulanan özellikler](docs/IMPLEMENTED-FEATURES.md)
- [Mimari kararlar](docs/ARCHITECTURE-DECISIONS.md)
- [API endpoint'leri](docs/API-ENDPOINTS.md)
- [Veritabanı şeması](docs/DATABASE-SCHEMA.md)
- [Güvenlik notları](docs/SECURITY-NOTES.md)
- [Bilinen sorunlar](docs/KNOWN-ISSUES.md)
- [Runbook](docs/RUNBOOK.md)
- [Sonraki adımlar](docs/NEXT-STEPS.md)
- [Teslim raporu](CODEX-HANDOVER.md)
