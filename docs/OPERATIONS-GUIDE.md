# Operasyon Kılavuzu

## Kurulum

Online compose için `compose.production.yaml`; air-gap için
`docs/OFFLINE-INSTALLATION.md` ve `scripts/install-offline.sh` kullanılır. Secret’lar
environment/secret store’dan sağlanır; default production secret yoktur.

## Health ve monitoring

Actuator liveness/readiness, Prometheus metrikleri ve OTel trace’leri izlenir.
`GET /api/v1/system/version` public-safe sürüm bilgisidir.
`POST /api/v1/operations/diagnostic-bundles` 24 saatlik sanitize manifest üretir.

## Backup/restore

`scripts/backup.sh` encrypted manifestli backup sözleşmesidir.
`scripts/restore-test.sh` yalnız staging validator’dır. Her release öncesi gerçek
backup/restore evidence release gate’e bağlanmalıdır.

## Upgrade/rollback

Backup → image digest doğrulama → migration → smoke/E2E/AI sample → rollback →
eski sürüm doğrulama → tekrar upgrade akışını izleyin. İstek ve gerçek sonuç ayrı
kaydedilir.

## Incident ve DLQ

`docs/runbooks/` altındaki backend, database, disk, MinIO, model, parser, RabbitMQ,
outbox, restore, security ve DLQ runbook’larını kullanın. Hotfix kapsamını kritik
incident ile sınırlayın.

## Model/parser

Queue/backpressure ve availability sinyallerini izleyin. Capacity doluyken READY
göstermeyin; iş queue’da kalmalı, override audit edilmelidir.
