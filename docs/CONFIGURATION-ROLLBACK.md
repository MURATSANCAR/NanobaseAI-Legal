# Configuration Rollback

Snapshot’lar immutable’dır; rollback eski kaydı yerinde değiştirmez.
`configuration_activation_history` yeni `ROLLBACK` kaydı üretir.

Kurallar:

- active ve target snapshot tenant kapsamlı olmalı
- target, active snapshot’ın kayıtlı predecessor’ı olmalı
- iki farklı approver gerekli
- reason ve audit/outbox event zorunlu
- tarihsel analiz sonuçları değişmez
- yalnız yeni işlemler target snapshot’ı kullanır

API: `POST /api/v1/configuration-activations/rollback`.
