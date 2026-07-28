# Database Failure

**Belirti:** pool exhaustion, timeout, migration/readiness failure. **Neden:** failover, TLS,
long transaction, disk veya connection leak. **Kontrol:** DB health, active/idle transaction,
slow query ve pool metrics; query textte document content paylaşma. **Müdahale:** admission
control, read traffic azaltma, approved failover; blocking session sonlandırma change kaydı ister.
**Geri alma:** failover sonrası eski primary’yi otomatik promote etme. **Veri riski:** RPO/WAL
gap. **Eskalasyon:** DBA+SRE derhal; security tenant/RLS şüphesinde incident.
