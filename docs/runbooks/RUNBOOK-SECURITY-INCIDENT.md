# Security Incident

**Belirti:** malware, tenant rejection spike, audit failure, secret/log leak veya privilege
anomaly. **Neden:** saldırı, config, compromised account/dependency. **Kontrol:** correlation/
event metadata, immutable audit, IdP events; hassas içeriği normal ticket’a alma. **Müdahale:**
isolate, token/secret revoke, affected tenant scope, forensic snapshot, legal/KVKK timeline;
scan/RLS bypass etme. **Geri alma:** yalnız incident commander. **Veri riski:** unknown until
forensics. **Eskalasyon:** derhal Security+SRE+Legal/DPO, P1.
