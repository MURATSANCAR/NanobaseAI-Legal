# v1.0 RC Test Matrix

Tarih: 2026-07-28. `Başarılı` yalnız bu çalışma sırasında çalıştırılan sonucu gösterir.
Bir alanın kod/test senaryosu bulunması runtime gate’in geçtiği anlamına gelmez.

| Test alanı | Test sayısı | Başarılı | Başarısız | Blocker | Kanıt |
| --- | ---: | ---: | ---: | ---: | --- |
| Unit | 113 | 113 | 0 | Hayır | Maven Surefire |
| Integration | 6 | 0 | 0 | Evet | Testcontainers: 6 SKIPPED, Docker yok |
| Contract | 20 | 20 | 0 | Hayır | Java adapter 3 + AI orchestrator 17 |
| Architecture | 31 | 31 | 0 | Hayır | ArchUnit 17 + hardening 8 + Sprint 9 6 |
| Frontend | 18 | 18 | 0 | Hayır | Node rendered-source tests |
| E2E | 4 | 0 | 0 | Evet | Playwright listesi; real stack çalışmadı |
| Security | 12 | 12 | 0 | Evet | 5 file unit + 2 prompt + 5 shell architecture; pentest/runtime yok |
| Performance | 3 | 0 | 0 | Evet | k6 portal/upload/analysis çalışmadı |
| Chaos | 0 | 0 | 0 | Evet | Çalıştırılmadı |
| Backup | 1 | 0 | 0 | Evet | Script syntax geçti; gerçek backup yok |
| Restore | 1 | 0 | 0 | Evet | Script syntax geçti; gerçek restore yok |
| Offline installation | 1 | 0 | 0 | Evet | Script syntax geçti; air-gap yok |
| Upgrade | 1 | 0 | 0 | Evet | Script syntax geçti; staging run yok |
| Rollback | 1 | 0 | 0 | Evet | Script syntax geçti; staging run yok |
| AI evaluation | 34 | 34 | 0 | Evet | 15 + 19 contract-golden; müşteri/model gate değil |
| Regression | 13 | 13 | 0 | Evet | Sprint 9 policy/architecture; ürün mandatory suite yok |
| UAT | 15 | 0 | 0 | Evet | UAT-01…15 `NOT_RUN` |

Ek kanıt: document intelligence 4/4, AI orchestrator 17/17, frontend lint ve build,
shell syntax, 5/5 architecture shell taraması başarılıdır.

## RC kararı

Matris production RC kabul kriterlerini karşılamaz. Integration, E2E, performance,
recovery, offline, upgrade/rollback, müşteri AI gate ve UAT blocker’dır.
