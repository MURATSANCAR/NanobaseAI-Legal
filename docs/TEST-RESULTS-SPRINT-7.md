# Sprint 7 Test Sonuçları

Tarih: 2026-07-28.

## Backend

Komut:

```text
JAVA_HOME=<bundled-jdk-21> mvn test
```

Son başarılı koşum: 89 test, 0 failure, 0 error; BUILD SUCCESS.
`DynamicWorkflowEnginesTest` condition DSL, parallel simulation, assignment,
minimum/weighted approval, business calendar, finalization/decision gate,
notification sanitization ve PDF/DOCX/XLSX renderer’larını kapsar.

`ArchitectureTest` workflow enum bağımlılığı, extension portları ve controller
sınırını; `PlatformInfrastructureIT` V13 tablo beklentilerini kapsar.

## Frontend

Komut:

```text
pnpm test
```

Vinext production build başarılıdır. Source-contract suite workflow designer,
backend-configured kolon/concept’ler ve task/approval/clarification/report/decision
API yollarını doğrular. Son tam koşum bu belge tesliminde yeniden çalıştırılır.

## Skip / ölçülmeyen

Docker komutu/daemon olmadığı için Testcontainers PostgreSQL, RLS, RabbitMQ, Redis ve
MinIO integration testleri skip edilir. Browser E2E, SSE progress, SLA zaman ilerleme
testi ve Sprint 7 yük ölçümü yapılmamıştır. Bunlar başarı olarak raporlanmaz.
