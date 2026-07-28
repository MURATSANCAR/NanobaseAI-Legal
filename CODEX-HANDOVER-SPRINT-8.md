# CODEX Handover — Sprint 8

Tarih: 2026-07-28. İlke: çalıştırılmayan hiçbir test veya runtime kontrolü başarılı
gösterilmemiştir.

## 1. Sprint 1–7 ön koşul doğrulaması

Kod envanteri authentication, RLS, storage, outbox/idempotency, document/Docling/OpenContracts,
requirement, ontology/terminology/policy/model/prompt/grounding, knowledge/evidence/compliance,
risk/conflict/impact ve Sprint 7 workflow şemasını gösterir. Ayrıntı
`docs/PRODUCTION-READINESS-BASELINE.md`; çoğu `PARTIALLY_VERIFIED`.

## 2. Production readiness seviyeleri

Backend/frontend Level 1; diğer critical services Level 0 runtime kanıtındadır. Hedef Level 5.
Hiçbir kritik servis operable/recovery kanıtına sahip değildir.

## 3. Güvenlik geliştirmeleri

Fail-closed file scan, archive/PDF preflight, quota/backpressure, RS256 issuer/audience, production
TLS/config guard, read-only/cap-drop containers, policy schemas ve supply-chain workflow eklendi.

## 4. Threat model sonuçları

STRIDE modeli tenant leak, IDOR/EoP, prompt/file/parser/model DoS, signed URL, audit/outbox,
workflow/template/log ve supply chain risklerini owner/test ile sınıflandırır. Critical residual
riskler açık.

## 5. Authorization testleri

Role/tenant/project matrix yazıldı. Mevcut ProjectAccess/MVC testleri geçti. Bütün endpoint × role
× membership × state matrisi ve gerçek JWT negatif testleri çalıştırılmadı.

## 6. Dosya güvenliği

Temp/quarantine prefix → preflight → ClamAV INSTREAM → SAFE → finalize → outbox sırası vardır.
Unsafe result parser’a gitmez. 5 unit test geçti; gerçek daemon/EICAR yok.

## 7. Parser sandbox

Non-root, read-only, bounded tmpfs, cap-drop, no-new-privileges, pids/CPU/RAM/timeout; DB
credential yok ve host port yok. Egress/prefix credential runtime testi yok.

## 8. Prompt injection güvenliği

Instruction signals, untrusted classification/delimiter, context isolation, tool disable, strict
schema, grounding/output validation ve manual review persistence eklendi. Python testleri
izole servis ortamlarında çalıştırıldı; prompt security testleri 2/2 geçti.

## 9. Identity hardening

Keycloak brute force, password/session/rotation/audit/TOTP support; backend RS256+issuer+audience+
clock skew. Gerçek MFA/revoke/disabled account smoke yok.

## 10. RLS ve database güvenliği

V14 tenant tabloları FORCE RLS. Testcontainers RLS testi mevcut ama Docker yokluğu nedeniyle
atlandı. Production role/grant/TLS timeout uygulaması deployment blocker.

## 11. MinIO güvenliği

Private bucket, tenant prefix, short authorized URL, quarantine/final hash. TLS/SSE/versioning/
lifecycle/object lock/restore runtime doğrulanmadı.

## 12. Encryption

Production guard PostgreSQL verify-full ve HTTPS ister; Rabbit/Redis TLS profile, encrypted `age`
backup sözleşmesi vardır. Certificate/storage runtime yok.

## 13. Audit integrity

Append-only trigger + tenant SHA-256 hash chain + tenant advisory lock + scheduled verifier
metric eklendi. WORM archive açık risk; DB migration testi koşulmadı.

## 14. Supply-chain sonuçları

CI SAST/SCA/secret/container/SBOM/license/IaC gates tanımlı; local architecture scan geçti.
Scanner/image build/sign çalışmadı.

## 15. SBOM ve lisans raporu

SBOM workflow’u var, artefact üretilmedi. OpenContracts current MIT, Docling code MIT, PDF.js
Apache-2.0; MinIO AGPL, ClamAV GPL, Redis 8 multi-license ve model weights legal blocker.

## 16. Container hardening

Fixed tags, non-root app images, healthchecks, multi-stage backend/frontend, read-only/cap/resource
controls. Digest/sign/Trivy evidence yok.

## 17. Rate limiting

Upload/signed URL/processing/SSE/search/admin ayrı external config policy; user/tenant/IP ve upload
size sinyali, Redis atomic counter vardır.

## 18. Tenant quota

Definition/assignment şeması ve seeded resource codes vardır. Upload storage/document count
enforced; diğer kaynaklar henüz bağlı değil.

## 19. Backpressure

Rabbit depth ve fail-closed broker state upload admission’ı yönetir; 503 Retry-After verir.
Model/GPU/ETA ve saturation runtime yok.

## 20. Circuit breaker

Document provider breaker, orchestrator bounded retry/fallback, outbox retry/backoff korunmuştur.
MinIO/Keycloak/SMTP için birleşik resilience policy ve chaos sonucu yok.

## 21. Load test sonuçları

k6 portal/upload/analysis/tenant isolation senaryoları var. Çalıştırılmadı; sayı üretilmedi.

## 22. Büyük belge testleri

Çalıştırılmadı. 10–1000 sayfa/OCR/table/column/DOCX corpus metrikleri boş.

## 23. Chaos/recovery sonuçları

Çalıştırılmadı. Kod-level retry/idempotency recovery kanıtı değildir.

## 24. Backup sonucu

Encrypted/manifested backup script var; backup alınmadı.

## 25. Restore sonucu

Staging-only restore validator var; restore yapılmadı.

## 26. RPO/RTO ölçümü

Policy şeması var; ölçüm yok.

## 27. Observability

Prometheus/Actuator, OTel bridge/exporter, correlation/tenant/job metadata ve ECS production log
config vardır. Collector/dashboard/runtime trace yok.

## 28. Alerting ve runbook’lar

Prometheus alert rules ve istenen 13 runbook oluşturuldu. Alert firing/delivery/on-call drill yok.

## 29. AI evaluation

Result-item/comparison metadata ve multi-layer architecture eklendi. Gerçek model run yok.

## 30. Golden dataset durumu

Müşteri golden dataset yok. Güvenli synthetic markdown set vardır ve acceptance yerine sayılmaz.

## 31. Quality gate sonuçları

Fail-closed multi-metric evaluator’ın 2 unit testi geçti. Activation integration yok.

## 32. Shadow/canary

Tenant/project/group/traffic ve rollback snapshot şemaları var. Execution router yok.

## 33. Pilot hazırlığı

Beş fazlı plan ve admin pilot readiness paneli var. Müşteri ingestion/golden/UAT eksik.

## 34. UAT sonuçları

UAT-01…15 `NOT_RUN`.

## 35. Security test sonuçları

Archive/PDF/ClamAV protocol unit testleri geçti; RLS/JWT/DAST/pentest/container/malware runtime
testleri eksik. Sonuç NO-GO.

## 36. Offline kurulum sonucu

Package/install/validate/health scriptleri var; image/model bundle ve air-gap test yok.

## 37. Upgrade/rollback sonucu

Backup-first rollout ve image-only rollback scripts syntax-checked; gerçek test yok.

## 38. E2E test sonuçları

Playwright serial real-stack senaryosu eklendi. Credential, valid fixture ve runtime olmadığı için
koşulmadı.

## 39. Veri retention ve KVKK

Retention/classification versioned schemas ve policy docs var. Masking/deletion/DSR enforcement
tam değil.

## 40. Operasyon ekranları

Tenant/system admin’a özel service/workload/audit/recovery blocker, AI quality ve pilot readiness
panelleri eklendi.

## 41. Go-live blocker’ları

Real-stack E2E, RLS, ClamAV, load/large docs, chaos, backup/restore/RPO-RTO, golden/model quality,
UAT, offline/rollback, pentest, exact license/SBOM/signature ve customer sign-off.

## 42. Çalıştırılan komutlar

- `JAVA_HOME=... mvn -B verify` → 97/97 unit başarılı; Docker olmadığı için 6/6
  Testcontainers integration testi atlandı; build başarılı.
- `pnpm install --lockfile-only`, `pnpm test` → production build ve 16/16 rendered HTML testi
  başarılı.
- `pnpm lint` → başarılı.
- `pnpm exec playwright test --list` → 4 real-stack E2E senaryosu keşfedildi; runtime
  çalıştırılmadı.
- İzole venv içinde iki servis ayrı çalıştırıldı: document intelligence 4/4, AI orchestrator
  17/17 başarılı.
- `python3 -m py_compile ...` → başarılı.
- `bash -n scripts/*.sh` → başarılı.
- `bash scripts/architecture-test.sh` → 5/5 başarılı.
- Docker/k6/Playwright real-stack runtime çalıştırılmadı.

## 43. Başarısız testler

İlk frontend ara build’i yanlış shared API import’u nedeniyle başarısız oldu; import
`apiRequest` ile düzeltildi ve final build/test geçti. Frontend lint ilk koşumda mevcut Sprint 7
hook/helper sorunlarını buldu; küçük lifecycle/helper düzeltmeleri sonrası geçti. İki Python
servisini tek pytest sürecinde koşmak aynı adlı `app.py` modüllerini çakıştırdı; servisler ayrı
çalışma dizinlerinde yeniden koşuldu ve 21/21 test geçti. İlk tool komutlarında sistem PATH’inde
Java/npm/pytest bulunmadı; bundled runtime kullanıldı. Bunlar gizlenmemiştir.

## 44. Tamamlanamayan alanlar

Runtime gerektiren tüm kabul; full endpoint auth matrix; remaining quotas; model backpressure;
shadow/canary executor; retention/masking worker; workflow/report E2E.

## 45. Bilinen riskler

`docs/KNOWN-ISSUES.md` içindeki 12 madde geçerlidir. En kritikler runtime kanıt yokluğu, model
license ve MinIO/Redis/ClamAV dağıtım lisanslarıdır.

## 46. Production önerisi

**NO-GO.** Kod/demo hardening önemli ölçüde ilerledi, fakat Level 5 service, real-stack security,
recovery, performance, model quality ve müşteri kabul kanıtları oluşmadan production onayı
verilmemelidir.
