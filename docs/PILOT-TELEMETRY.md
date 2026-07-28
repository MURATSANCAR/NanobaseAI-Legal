# Pilot Telemetry

V15 migration’ı `pilot_session`, `pilot_event`, `pilot_metric_definition` ve
`pilot_metric_snapshot` tablolarını tenant-RLS ile oluşturur.

## Veri minimizasyonu

`pilot_event.metadata_json` yalnız şu anahtarları kabul eder:

`duration_ms`, `page_count`, `clause_count`, `requirement_count`,
`model_profile`, `schema_failure`, `grounding_result`, `retry_count`,
`manual_review`, `expert_decision_type`, `correction_type`,
`queue_duration_ms`, `parser_warning_code`, `ocr_quality_level`.

Veritabanı trigger’ı ve uygulama allowlist’i birlikte çalışır. 32 KiB sınırı vardır.
Doküman/evidence metni, prompt, ham model giriş/çıkışı, token, secret, signed URL,
kişisel veri ve ticari sır yasaktır. Boyut alanlarında da hassas anahtar denylist’i
uygulanır.

## API

- `POST /api/v1/pilot-sessions`
- `GET /api/v1/pilot-sessions/{id}`
- `POST /api/v1/pilot-sessions/{id}/events`
- `POST /api/v1/pilot-sessions/{id}/metrics`
- `GET /api/v1/pilot-quality-dashboard`

Telemetry ürün davranışını otomatik değiştirmez; yalnız ölçüm ve insan triage girdisidir.
