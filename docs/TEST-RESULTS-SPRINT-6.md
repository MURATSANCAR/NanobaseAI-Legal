# Sprint 6 Test Sonuçları

Tarih: 2026-07-28

## Çalıştırılan kontroller

| Komut | Sonuç |
|---|---|
| `mvn test` (Temurin Java 21.0.11, Maven 3.9.9) | 71 test, 0 failure, 0 error |
| `mvn verify` | Build başarılı; Docker gerektiren 5 integration testi skip |
| `pnpm run test` | Production build + 13 Node contract testi başarılı |
| `pytest -q` (repository Python servisleri) | 19 test başarılı |
| `python evaluation/evaluate_sprint6.py` | 19 contract-golden case raporlandı |

`mvn test` risk signal, confidence, exposure, ambiguity, staged conflict retrieval,
structured conflict, change matching, impact traversal, propagation ve
architecture kurallarını kapsar.

## Contract-golden evaluation

Bu sonuç model benchmark’ı değil; deterministik provider ve AI çıktı guard
sözleşmelerinin küçük, sentetik golden set ölçümüdür.

| Metrik | Sonuç |
|---|---:|
| Case sayısı | 19 |
| Risk precision / recall | 1.00 / 1.00 |
| Conflict precision / recall | 1.00 / 1.00 |
| Ambiguity precision / recall | 1.00 / 1.00 |
| Severity accuracy | 1.00 |
| Probability Brier score | 0.06625 |
| Impact accuracy | 1.00 |
| Source grounding | 1.00 |
| Authority decision accuracy | 1.00 |
| Change matching accuracy | 1.00 |
| Staleness detection accuracy | 1.00 |
| Manual review oranı | 0.9474 |
| LLM çağrı oranı | 0.1053 |
| Deterministik çözüm oranı | 0.8947 |
| Token kullanımı | 770 |
| Ortalama sentetik analiz süresi | 16.53 ms |

Production precision/recall ve calibration için müşteri-onaylı dataset ile lokal
model deployment’ı gerekir; bu sayılar production kalite iddiası değildir.

## Doğrulanamayanlar

Docker daemon yoktu. Bu nedenle `mvn verify` içindeki Testcontainers PostgreSQL
17/RLS/RabbitMQ/MinIO/Redis testleri runtime’da çalışmadı. SQL migration’lar
compile ve static incelemeden geçti; entegrasyon doğrulaması CI/Docker hostunda
zorunludur.
