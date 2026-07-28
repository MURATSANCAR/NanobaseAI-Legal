# Ambiguity Engine

`ConfigurableAmbiguityAnalysisEngine`, requirement’ın structured attributes
alanlarından policy `featureSources` JSON pointer’larıyla özellik çıkarır.
Measurement, operator, test condition veya acceptance threshold gibi adlar kodda
karar listesi değildir; aktif policy tarafından seçilir.

Motor her feature için input/weight/effect, toplam confidence, missing fields ve
ontology concept döndürür. Finding threshold policy’dedir. Sonuç
`ambiguity_finding`, PDF bağlantısı `ambiguity_source`, olası yorumlar
`ambiguity_interpretation` tablolarında tutulur.

Uzman onay/red kararı audit ve `expert_feedback` üretir. Clarification önerisi
yalnız `CANDIDATE` durumunda oluşturulur.
