# Conflict Comparison Strategies

`ConflictComparisonStrategy` provider sözleşmesi `supports` ve `compare`
operasyonlarını tanımlar. `DefaultConflictStrategyRegistry` ilk destekleyen
provider’a yönlendirir.

`StructuredValueConflictStrategy`, policy `structuredRules` içindeki JSON
pointer, tolerance, provider code ve conflict concept’i kullanır. Numeric ve
duration farkları modele bırakılmadan çözülür. Authority bilinmiyorsa tercih
üretmez.

Provider sonucu iki supporting source ID taşımak zorundadır.
`JdbcRiskPersistence.createConflict` her iki ID’nin seçilmiş aday çifti olduğunu
yeniden doğrular. Yeni date/range/logical/LLM strategy aynı interface ile
eklenebilir.
