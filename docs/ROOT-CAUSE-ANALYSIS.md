# Kök Neden Analizi

`ErrorRootCauseAnalyzer` arayüzü promptta verilen sözleşmeyi uygular.
`ExplainableErrorRootCauseAnalyzer`, feedback type prior’ı ile açık runtime
sinyallerini policy ağırlıkları üzerinden birleştirir.

Çıktı:

- primary cause
- güven skoru
- sinyal/effect/evidence taşıyan katkı faktörleri
- önerilen inceleme alanları
- okunabilir açıklama
- `humanApprovalRequired=true`

Analyzer önerisi final triage değildir. Kullanıcı kendi primary cause kararını kaydeder;
öneri ayrıca immutable audit snapshot olarak tutulur. LLM ileride adapter olarak yardım
edebilir, ancak aynı insan onayı kuralına tabidir.
