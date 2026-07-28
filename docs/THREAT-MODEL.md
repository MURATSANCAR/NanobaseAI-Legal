# Threat Model

Yöntem: STRIDE. Trust boundary’ler browser/gateway, identity, backend, data plane,
parser sandbox ve model runtime’dır. Varlıklar şartname/firma/ürün/personel belgeleri,
sertifikalar, AI sonuçları, prompt/policy/model config, rapor, audit, hesap ve secret’lardır.

| Tehdit | Risk / STRIDE | Saldırı yolu | Mevcut kontrol | Eksik kontrol | Düzeltme ve test | Sorumlu |
|---|---|---|---|---|---|---|
| Tenant sızıntısı/IDOR | Kritik / I,E | Başka tenant UUID’si | JWT tenant claim, repository scope, FORCE RLS | Gerçek role negative test | İki tenant sentinel corpus; API+SQL kaçış testi | Backend/DB |
| Yetki yükseltme | Kritik / E | Rol/audience sahteciliği | RS256 allowlist, issuer/aud, method roles | Gerçek Keycloak revoke/MFA smoke | Tampered/revoked token matrisi | Identity |
| Prompt injection | Yüksek / T,I | Belge içi talimat | Untrusted JSON/delimiter, tool yok, strict schema, grounding, sinyal skoru | Model-red-team corpus | Injection corpus; sistem/şema/tool invariant testi | AI |
| Malicious PDF/parser exploit | Kritik / T,D,E | Crafted PDF/DOCX | MIME/magic/ext, ClamAV, encryption/page/archive sınırı, sandbox | Gerçek malware corpus | EICAR, parser CVE fixture, timeout/memory test | Upload/parser |
| ZIP bomb/nested archive | Yüksek / D | Yüksek expansion/nested payload | Entry/byte/ratio/path/nested sınırı | Büyük gerçek fixture | Unit + container resource ölçümü | Upload |
| Büyük dosya/model DoS | Yüksek / D | Upload/job/token flood | Size limit, rate policy, quota, backpressure | Saturation runtime | k6 + queue/model capacity chaos | Platform/AI |
| Signed URL sızıntı/replay | Yüksek / I | Log/referrer/forward | Yetki sonrası tek object, 5 dk, URL loglanmaz | Tek kullanım desteği yok | Expiry, foreign tenant, log scan | Storage |
| Rapor erişim ihlali | Kritik / I | Snapshot/artifact IDOR | Tenant/member authorization deseni | Report API E2E yok | Cross-tenant artifact test | Reporting |
| Audit manipülasyonu | Kritik / T,R | UPDATE/DELETE/reorder | Append-only trigger, tenant RLS, SHA-256 chain, verifier metric | WORM offsite yok | SQL mutation ve chain-corruption test | Audit/SRE |
| Outbox replay/poison | Yüksek / T,D | Duplicate/bad payload | Idempotency, retry cap, DLQ, schema envelope | Broker chaos kanıtı yok | Duplicate, crash, poison payload test | Messaging |
| Template injection | Yüksek / T,E | Report/notification template | Versioned templates, output DTO | Renderer allowlist kanıtı yok | SSTI payload suite | Reporting |
| Unsafe workflow expression | Kritik / E | Script/expression injection | Safe JSON condition engine, no eval | Fuzz/complexity limit | Forbidden expression and timeout tests | Workflow |
| Hassas veri loglama | Yüksek / I | Exception/raw prompt log | Structured metadata; orchestrator logs signal only | Otomatik log DLP scan yok | Sentinel secret log scan | All services |
| Supply-chain | Kritik / T,E | Dependency/image/action compromise | Locks, pinned images, SAST/SCA/SBOM workflow | Image signing/release evidence yok | Trivy/Gitleaks/Checkov/SBOM/Cosign gate | DevSecOps |

Residual risk kabulü yalnız owner, son tarih ve telafi kontrolü içeren go-live kaydıyla yapılır.
