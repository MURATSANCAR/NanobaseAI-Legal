# Outbox Dead

**Belirti:** `outbox_dead_total>0`. **Neden:** schema/routing, broker veya retry exhaustion.
**Kontrol:** event id/type/retry/error code; payload içeriğini ticket’a kopyalama. **Müdahale:**
consumer compatibility’yi düzelt, tek event için audited replay kullan. **Geri alma:** replay
değil forward-fix; duplicate idempotent olmalı. **Veri riski:** domain state yayımlanmamış.
**Eskalasyon:** application owner ve gerekiyorsa domain owner.
