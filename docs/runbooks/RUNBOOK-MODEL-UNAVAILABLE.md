# Model Unavailable

**Belirti:** model timeout/502, queue latency. **Neden:** runtime/GPU/model file/capacity.
**Kontrol:** orchestrator readiness, deployment profile, GPU/RAM, model queue; raw prompt
görüntüleme. **Müdahale:** backpressure, approved fallback profile veya pause; mock sonuç
üretme. **Geri alma:** model/prompt/policy snapshot rollback quality gate’e tabidir.
**Veri riski:** incomplete job; stale sonucu final rapora alma. **Eskalasyon:** AI/SRE.
