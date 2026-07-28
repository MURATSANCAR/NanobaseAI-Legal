# Sprint 9 Ön Koşul Doğrulaması

Tarih: 2026-07-28. Bu tablo `CODEX-HANDOVER-SPRINT-8.md`, repository kodu,
mevcut test raporları ve bu sprintte gerçekten çalıştırılan komutlardan türetilmiştir.
Bir dosyanın varlığı runtime başarısı sayılmamıştır.

| Bileşen | Kod kanıtı | Test kanıtı | Runtime kanıtı | Pilot kanıtı | Durum |
| --- | --- | --- | --- | --- | --- |
| Production readiness baseline | `docs/PRODUCTION-READINESS-BASELINE.md` | Mimari testler | Critical servisler Level 0/1 | Yok | PARTIAL |
| Threat model | `docs/THREAT-MODEL.md` | Güvenlik mimari taraması | Pentest yok | Yok | PARTIAL |
| Authorization matrix | `docs/AUTHORIZATION-MATRIX.md`, `SecurityConfig` | ProjectAccess testleri | Tam JWT negatif matrisi yok | Yok | PARTIAL |
| File security | fail-closed scan/preflight | Archive 3/3, ClamAV protocol 2/2 | Gerçek ClamAV/EICAR yok | Yok | PARTIAL |
| Parser sandbox | hardened compose/Dockerfile | Mimari tarama | Egress/sandbox runtime yok | Yok | PARTIAL |
| Prompt injection security | orchestrator guards | 2/2 prompt security | Gerçek model adversarial run yok | Yok | PARTIAL |
| RLS tests | V8, V14, V15 FORCE RLS | Testcontainers testi tanımlı | Docker yok; koşulamadı | Yok | BLOCKED |
| MinIO security | tenant prefix/private bucket | object storage unit 2/2 | TLS/SSE/restore yok | Yok | PARTIAL |
| Audit integrity | hash chain/append-only verifier | mimari doğrulama | PostgreSQL migration/runtime yok | Yok | PARTIAL |
| SBOM | supply-chain workflow | Workflow syntax mevcut | Artifact üretilmedi | Uygulanamaz | NOT_AVAILABLE |
| Container scans | CI gate tanımlı | Yerel scan yok | Image scan yok | Uygulanamaz | NOT_AVAILABLE |
| Load tests | `load/k6/*.js` | Senaryolar mevcut | k6 çalıştırılmadı | Yok | NOT_AVAILABLE |
| Chaos tests | recovery tasarımı | Yok | Çalıştırılmadı | Yok | NOT_AVAILABLE |
| Backup | `scripts/backup.sh` | Shell syntax başarılı | Backup alınmadı | Yok | PARTIAL |
| Restore | `scripts/restore-test.sh` | Shell syntax başarılı | Restore yapılmadı | Yok | PARTIAL |
| RPO/RTO | policy ve doküman | Yok | Ölçüm yok | Yok | PARTIAL |
| Observability | Actuator, Prometheus, OTel, Sprint 9 metrikleri | Spring testleri | Collector/dashboard yok | Yok | PARTIAL |
| Alerting | Prometheus rules | Yapısal kanıt | Firing/delivery drill yok | Yok | PARTIAL |
| Runbooks | `docs/runbooks/` | 13 runbook mevcut | Tatbikat yok | Yok | PARTIAL |
| Evaluation infrastructure | dataset/run/result, V15 experiments | 34 contract-golden vaka çalıştı | Gerçek model/customer data yok | Yok | PARTIAL |
| Golden dataset | synthetic + contract-golden | Sprint 5: 15, Sprint 6: 19 | Müşteri golden dataset yok | Yok | PARTIAL |
| Quality gates | fail-closed evaluator + RC gates | policy testleri başarılı | Production gate sonucu yok | Yok | PARTIAL |
| Shadow mode | V14/V15 + API/event zinciri | mimari test | Worker/runtime sonucu yok | Yok | PARTIAL |
| Canary | assignment, rollback snapshot, fail-closed API | policy test | Runtime canary yok | Yok | PARTIAL |
| Pilot onboarding | `docs/PILOT-PLAN.md` | Yok | Gerçek onboarding yok | Yok | PARTIAL |
| UAT | plan ve 15 vaka | 0/15 çalıştırıldı | Yok | Müşteri imzası yok | NOT_AVAILABLE |
| Offline installation | package/install/validate scriptleri | Shell syntax başarılı | Air-gap kurulum yok | Yok | PARTIAL |
| Upgrade/rollback | backup-first scriptler | Shell syntax başarılı | Dry run yok | Yok | PARTIAL |

## Sonuç

Sprint 9 geliştirmesi için kod tabanı uygundur; v1.0 RC veya production onayı için
uygun değildir. Runtime, müşteri ve recovery kanıtları açık blocker’dır.
