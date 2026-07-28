# Sprint 7 Güvenlik Notları

Uygulanan kontroller:

- JWT + method/request authorization; tenant id kullanıcı payload’ından alınmaz.
- Tüm Sprint 7 tenant tablolarında `organization_id`, RLS ve FORCE RLS.
- Safe JSON condition DSL; SpEL, script ve reflection yok.
- Versioned definition/policy/template; aktif sürüm yerinde mutate edilmez.
- Approval/task/finalization history append-only iş akışı.
- Notification allowlist sanitizer.
- Snapshot tabanlı report/decision; stale policy gate.
- İnsan executive decision’ı otomatik öneriden ayrıdır.
- Artifact download erişim kontrollü URL üzerinden.
- Correlation ID, audit ve outbox event provenance.
- Architecture test sabit workflow enum bağımlılığını ve controller-engine
  karışmasını engeller.

Doğrulanmamış veya eksik kontroller:

- Docker olmadığı için gerçek PostgreSQL RLS cross-tenant integration testi skip.
- Signed object-storage URL ve RabbitMQ duplicate delivery E2E çalışmadı.
- Field-level report masking policy renderer’a tam bağlı değil.
- Approval delegation için ayrı auditli domain nesnesi yok.
- SLA scheduler ve notification retry/dead-letter canlı test edilmedi.
- Frontend source/build testli; signed-in browser authorization E2E yok.

Production öncesi CI’da `mvn verify`, migration/RLS Testcontainers, broker
idempotency, object-storage erişim ve OWASP DAST zorunludur.
