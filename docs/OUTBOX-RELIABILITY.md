# Outbox Güvenilirliği

V5 migration ve `OutboxStore` aşağıdaki durumları kullanır:

`PENDING → CLAIMED → PUBLISHED`

Publish hatasında `FAILED` + exponential backoff + jitter uygulanır. Yapılandırılan
maksimum retry sonrasında `DEAD` olur. `claimed_at` timeout’unu aşmış CLAIMED
kayıtlar başka replica tarafından yeniden claim edilir.

Pending, retryable ve expired claim sorguları transaction içinde
`FOR UPDATE SKIP LOCKED` kullanır. RabbitMQ correlated publisher confirm ACK
gelmeden ve mesaj unroutable dönmediği doğrulanmadan PUBLISHED yapılmaz.

Consumer idempotency `processed_message(consumer_name,event_id)` unique constraint’i
ve atomik upsert claim’iyle sağlanır. Başarısız işlem yeniden alınabilir; tamamlanmış
event ikinci kez işlenmez.

