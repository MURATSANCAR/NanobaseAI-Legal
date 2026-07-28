# Backup Failure

**Belirti:** backup job/manifest/encryption/offsite copy hatası. **Neden:** role, disk, key,
network, retention. **Kontrol:** job code, target capacity, last successful RPO; secret yazdırma.
**Müdahale:** güvenli hedef/key ile yeniden çalıştır; primary workload’u tehlikeye atma.
**Geri alma:** yok. **Veri riski:** RPO breach; go-live blocker. **Eskalasyon:** SRE/DBA/security.
