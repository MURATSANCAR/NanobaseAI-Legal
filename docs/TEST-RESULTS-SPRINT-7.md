# Sprint 7 Test Sonuçları

Tarih: 2026-07-28.

## Backend

Komut:

```text
JAVA_HOME=<bundled-jdk-21> mvn test
```

Son başarılı koşum: 100 test, 0 failure, 0 error, 0 skipped; BUILD SUCCESS
(24.781 saniye Maven süresi).
`DynamicWorkflowEnginesTest` condition DSL, parallel simulation, assignment,
minimum/weighted approval, business calendar, finalization/decision gate,
notification sanitization, SLA scheduler action ve PDF/DOCX/XLSX renderer’larını
kapsar. `Sprint7NotificationConsumerTest` geçerli event ve duplicate delivery
idempotency davranışını doğrular.

`ArchitectureTest` workflow enum bağımlılığı, extension portları ve controller
sınırını; `PlatformInfrastructureIT` V13 tablo beklentilerini kapsar.

## Frontend

Komut:

```text
pnpm test
```

Vinext production build başarılıdır. Source-contract suite workflow designer,
backend-configured kolon/concept’ler ve task/approval/clarification/report/decision
API yollarını doğrular. Son koşum: 16 test, 16 pass, 0 fail.

## Skip / ölçülmeyen

Docker komutu/daemon olmadığı için Testcontainers PostgreSQL, RLS, RabbitMQ, Redis ve
MinIO integration testleri bu koşuma dahil değildir. Browser E2E, SSE progress, canlı
SLA zaman ilerleme/broker testi ve Sprint 7 yük ölçümü yapılmamıştır. Bunlar başarı
olarak raporlanmaz.
