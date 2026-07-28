# DLQ Recovery

**Belirti:** DLQ growth/poison event. **Neden:** invalid schema, permanent dependency veya bug.
**Kontrol:** event envelope/error code/version; payload’ı redacted viewer ile aç. **Müdahale:**
root cause fix, dry-run validator, bounded audited replay; bulk replay yok. **Geri alma:** replay
stop; idempotency kayıtlarını silme. **Veri riski:** duplicate/ordering. **Eskalasyon:**
application + domain owner.
