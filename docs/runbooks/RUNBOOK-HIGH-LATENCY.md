# High Latency

**Belirti:** p95 budget ihlali. **Neden:** DB/query, queue, model, storage veya saturation.
**Kontrol:** trace ile span breakdown, queue age, pool, CPU/RAM; tenant cardinality güvenli
label. **Müdahale:** backpressure, costly feature flag’i güvenlik kontrolünü etkilemeden pause,
safe scale. **Geri alma:** config/image. **Veri riski:** timeout sonrası duplicate retry.
**Eskalasyon:** SRE→sorumlu span owner.
