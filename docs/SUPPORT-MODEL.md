# Support Model

| Seviye | Kapsam | Örnek owner |
|---|---|---|
| L1 | Kullanıcı, access, known workflow, status communication | Customer operations |
| L2 | Deployment, DB/queue/storage/identity integration, runbooks | SRE/platform |
| L3 | Code defect, security, AI/parser/model quality | Engineering/AI/security |

Severity policy tenant/deployment config’ten gelmelidir. P1 güvenlik/data loss/tenant isolation:
15 dk response, incident commander; P2 core outage: 30 dk; P3 degraded: 4 saat; P4 request:
1 business day başlangıç önerisidir. Resolution SLA, escalation, owner ve runbook ticket’ta
zorunludur. 24x7 staffing ve müşteri notification kanalı henüz doğrulanmadı.
