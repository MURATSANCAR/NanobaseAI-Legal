# Uzman Geri Bildirimi

PUT/review/split/merge işlemleri yalnız audit değildir:

- önceki ve düzeltilmiş snapshot,
- serbest feedback type,
- reason/reviewer,
- analysis/ontology/policy/prompt/model run version kimlikleri,
- `approved_for_learning`

ile `expert_feedback` tablosuna yazılır. Her değişiklik ayrıca
`requirement_revision` üretir. Source type metin alanıdır; yeni kaynak türü kodu kırmaz.

`approved_for_learning=true`, production davranışını değiştirmez. Kayıt yalnız offline
dataset'e seçilebilir hale gelir. Policy/prompt/ontology aktivasyonu ayrı evaluation ve
yönetici onay sürecidir. `expert.feedback.recorded.v1` olayı sonraki offline pipeline'a
güvenli tetik sağlar.
