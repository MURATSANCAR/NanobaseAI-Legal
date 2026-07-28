# RabbitMQ Backlog

**Belirti:** queue depth/age ve ETA artar. **Neden:** worker down, poison message, model/parser
yavaşlığı. **Kontrol:** queue depth/age, consumer count, DLQ, downstream health. **Müdahale:**
backpressure’ı koru, yalnız healthy worker ölçekle, poison message’i DLQ’ya izole et.
**Geri alma:** worker scale/config. **Veri riski:** ack öncesi tekrar teslim normaldir;
idempotency kontrol et. **Eskalasyon:** 15 dk platform, 30 dk downstream owner.
