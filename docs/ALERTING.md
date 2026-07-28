# Alerting

Alert threshold’ları deployment config/Prometheus rule dosyasındadır; uygulama koduna
dağıtılmaz. Her alert severity, owner, runbook, notification/silence policy taşır.

Zorunlu konular: backend down; DB pool; Rabbit/outbox/DLQ backlog; worker/parser/model failure;
grounding/security scan spike; disk/MinIO; audit integrity; backup/restore; cross-tenant reject;
login failure ve SLA breach.

Alert delivery ve silence/escalation runtime testi yapılmadı. P1 security/audit/restore alert’i
otomatik kapanmamalı; incident commander onayı gerekir.
