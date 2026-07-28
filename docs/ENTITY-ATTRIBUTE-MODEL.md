# Entity Attribute Modeli

`entity_attribute` sabit ürün kolonları yerine concept + typed value zarfı kullanır.
Platformun tanıdığı temel şekiller TEXT, NUMBER, RANGE, BOOLEAN, DATE, DATETIME,
DURATION, QUANTITY, REFERENCE, ENUM_CONCEPT ve JSON'dur; bunlar iş kategorisi değil
serileştirme şeklidir.

`DynamicValueValidator`, bilinmeyen bir `value_type` geldiğinde policy `REJECT`
demedikçe değeri `unsupportedMetadata.originalType` ile saklar. Böylece yeni renderer
ve validation policy daha sonra aktive edilebilir. RANGE başlangıç/bitiş tutarlılığı
ve zorunlu typed alanlar doğrulanır.

Her attribute `source_fragment_id` taşır; kaynaksız attribute API ve extraction
processor tarafından kabul edilmez. Sayısal birim, sabit string değil
`unit_concept_id` ile tutulur. UI renderer eşlemesi `ENTITY_PROFILE`
konfigürasyonundaki `valueRenderers` alanından gelir.
