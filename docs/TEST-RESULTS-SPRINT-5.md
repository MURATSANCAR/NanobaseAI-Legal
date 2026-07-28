# Sprint 5 Test Sonuçları

Tarih: 2026-07-28

## Backend

Komut:

```text
JAVA_HOME=<JDK21> /tmp/codex-maven/bin/mvn -q test
```

Sonuç: **71 test, 0 failure, 0 error**. Sprint 5 suite'i 8 test içerir:
dynamic/unsupported value, entity ambiguity, multi-signal reranking, numeric/range/date/
boolean/composite comparison, confidence/contradiction, evidence validity ve source
authority policy.

ArchUnit; domain'in fixed company/product/decision alanlarına bağımlı olmamasını,
retrieval/comparison'ın port arkasında kalmasını ve controller'ların bu motorları
doğrudan kullanmamasını doğrular.

## AI Orchestrator contract testleri

Komut, temiz geçici Python dependency diziniyle:

```text
PYTHONPATH=<temporary-requirements> python3 -m pytest -q
```

Sonuç: **15 passed**. Sprint 5 contract kapsamı valid response, request dışı evidence,
unsupported decision, ungrounded positive claim, schema rejection, timeout,
unavailable runtime, contradiction omission, retry ve fallback model senaryolarıdır.

## Frontend

Komutlar:

```text
pnpm test
pnpm run build
```

Sonuç: **13 test geçti**, Vinext production build başarılı. Knowledge Center,
evidence/PDF bağlantısı, dinamik entity profile, compliance matrix, backend decision
seçenekleri ve contradiction görünürlüğü source-contract testlerindedir.

Standalone `tsc --noEmit`, mevcut Cloudflare worker ambient type'ları
(`cloudflare:workers`, `Fetcher`, `D1Database`) nedeniyle kullanılmamıştır; production
builder bu tipleri başarıyla çözmüştür.

## Contract-golden evaluation

Dataset: `services/ai-orchestrator/evaluation/sprint5_cases.json`

| Ölçüm | Sonuç |
|---|---:|
| Case | 15 |
| Decision accuracy | 1.0000 |
| Grounding coverage | 0.8667 |
| Deterministic çözüm oranı | 0.8000 |
| LLM çağrı oranı | 0.2000 |
| Manual review oranı | 0.8000 |
| Toplam sentetik token | 1,385 |
| Ortalama analiz süresi | 28.87 ms |

Bu bir contract-golden regression setidir; gerçek müşteri dokümanı veya canlı model
quality benchmark'ı değildir.

## Çalıştırılamayan doğrulama

Hostta Docker CLI/daemon bulunmadığı için `PlatformInfrastructureIT` Testcontainers
paketi (PostgreSQL/Flyway/RLS, RabbitMQ, Redis, MinIO) çalıştırılamadı. V11/V12 canlı
migration ve cross-tenant SQL davranışı CI veya Docker hostunda `mvn verify` ile
zorunlu olarak tekrar edilmelidir.
