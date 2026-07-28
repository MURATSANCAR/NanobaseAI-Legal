# Security Test Results

| Test | Kanıt | Durum |
|---|---|---|
| IDOR/cross-tenant | Repository negative unit; RLS IT tanımlı ama Docker yok | PARTIAL |
| Privilege/JWT tamper/expired/revoked | RS256 issuer/aud config; gerçek IdP yok | NOT_RUN |
| Unsafe file/ZIP bomb/encrypted PDF | 5 unit test geçti | UNIT_VERIFIED |
| Malicious PDF/EICAR | Gerçek ClamAV daemon yok | NOT_RUN |
| Prompt injection | Python test eklendi, syntax geçti; pytest yok | NOT_RUN |
| Unsafe workflow expression | Safe JSON engine derlendi; fuzz yok | NOT_RUN |
| Signed URL replay | 5 dk single-object code; runtime yok | NOT_RUN |
| Rate bypass/quota/backpressure | Policy code ve tests derlendi; Redis/Rabbit yok | NOT_RUN |
| Mass assignment/SQLi/XSS/CSRF/open redirect | DTO/security design; DAST yok | NOT_RUN |
| Sensitive log/secret exposure | Architecture/Gitleaks workflow; scanner çalışmadı | PARTIAL |
| Dependency/container privilege | Trivy/Checkov workflow; Docker yok | NOT_RUN |

Sonuç: security blocker’lar açıktır; pentest/go-live onayı verilmez.

Pentest paketi bu doküman, `THREAT-MODEL`, `AUTHORIZATION-MATRIX`,
`NETWORK-FLOW-MATRIX`, OpenAPI endpoint’i, UAT planı ve known issues ile hazırlanır. Test user,
tenant sentinel, staging URL, log erişim ve acil iletişim müşteri/SRE tarafından dışarıdan
sağlanmalıdır.
