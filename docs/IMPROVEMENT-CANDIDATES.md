# Improvement Candidates

Bir hata active configuration’ı değiştirmez. `improvement_candidate`; root-cause
kaydı, candidate türü, target component, baseline ve candidate configuration
snapshot’ları, beklenen iyileşme ve risk değerlendirmesini bağlar.

Lifecycle:

`DRAFT → OFFLINE_EVALUATION → SHADOW → CANARY → APPROVED → ACTIVE`

Her aşama önceki kanıtı sorgular. Offline PASS olmadan shadow, başarılı shadow olmadan
canary, başarılı canary olmadan aktivasyon mümkün değildir. Başarısız evaluation
candidate’ı `REJECTED`, başarısız canary `ROLLED_BACK` yapar.

Candidate türleri dinamik katalogdadır; yeni tür Java değişikliği gerektirmez.
