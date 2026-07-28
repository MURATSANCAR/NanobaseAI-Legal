# NANObaseAI Technical Specification Intelligence v1.0 RC — Draft Release Notes

Durum: **DRAFT / NOT RELEASED**. Bu notlar yalnız repository migration, API ve UI
değişikliklerinden üretilmiştir; production release iddiası değildir.

## Yeni

- Tenant-RLS pilot telemetry ve feedback merkezi
- Dinamik hata sınıflandırma, explainable root-cause önerisi ve insan triage
- Sanitized immutable reproduction packages
- Configuration snapshot, improvement candidate ve experiment management
- Offline → shadow → canary → activation gate zinciri
- Regression, quality debt ve human adjudication modelleri
- Immutable digest-based release manifest, 20 dynamic RC gate
- Dynamic approval, dry-run, GO/NO-GO, deployment/rollback request-result ayrımı
- Stabilization/hypercare policy ve güvenli diagnostic bundle
- `GET /api/v1/system/version`
- Pilot kalite, üç panelli hata analizi, improvement ve RC/go-live portal ekranları

## Migration

`V15__pilot_stabilization_and_release_candidate.sql`.

## Güvenlik

Telemetry allowlist, recursive payload sanitizer, immutable snapshot/result/manifest,
RLS, double-approval rollback ve fail-closed evidence kontrolleri.

## Bilinen sorunlar

`docs/FINAL-KNOWN-ISSUES.md`. Runtime ve müşteri kanıtları eksik olduğu için release
önerisi NO-GO’dur.

## Rollback

Application/config rollback tarihsel sonuçları değiştirmez. Production rollback
yalnız evidence-backed request/result akışı ve runbook ile yapılmalıdır.
