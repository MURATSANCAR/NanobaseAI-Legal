# Observability

Spring Actuator/Prometheus ve Micrometer→OpenTelemetry OTLP eklidir. Request filters
correlation/tenant/user MDC taşır; job/document/project ID ilgili event’lerde bulunur. Python
servisleri correlation header’ı taşır ve raw content yerine error type/signal code loglar.

Production log formatı ECS JSON’dır. Yasak: parola/token/secret/signed URL, doküman/evidence/
personel/prompt, raw model input/output. Metric aileleri platform/storage/queue/AI/quality olarak
dashboards’da toplanmalıdır; `audit_integrity_failure_total` doğrudan uygulanmıştır.

Collector, trace propagation across RabbitMQ, dashboards ve cardinality/redaction runtime
kanıtı yoktur.
