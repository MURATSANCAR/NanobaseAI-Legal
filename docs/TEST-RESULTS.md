# Sprint 3 Test Sonuçları

Son doğrulama: 2026-07-28.

| Komut | Sonuç |
| --- | --- |
| `mvn clean verify` | Başarılı; 39 unit/contract/architecture test geçti |
| Testcontainers/Failsafe | 4 test keşfedildi, Docker bulunmadığı için 4 skip |
| `npm ci` | Başarılı; package-lock senkronize edildi |
| `npm run lint` | Başarılı |
| `npm run test` | Başarılı; 8/8 |
| `npm run build` | Başarılı |
| Python `pytest -q services/document-intelligence/test_app.py` | Başarılı; 4/4 |
| Python `py_compile` | Başarılı |
| Standalone Docker Compose v2.39.1 `config --quiet` | Başarılı |
| `docker compose build/up/ps/logs` | Çalıştırılamadı: Docker daemon/socket yok |
| Backend liveness/readiness curl | Stack açılamadığı için çalıştırılamadı |

System `docker` komutu yoktu; daemon gerektirmeyen config doğrulaması resmi
standalone Compose binary ile yapıldı. Testcontainers skip’leri başarı sayılmamıştır.
Gerçek PostgreSQL migration/RLS,
RabbitMQ publisher race, MinIO ve full upload→parse E2E doğrulaması Docker erişimli
CI’da zorunludur.
