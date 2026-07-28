# Compliance Confidence

`PolicyComplianceConfidenceEngine` sabit formül kullanmaz. Aktif policy
`weights`, `penalties`, `reviewBelow` ve opsiyonel `levels` dizisini taşır.

Desteklenen input sinyalleri catalog/policy anahtarlarıdır: relevance, validity,
authority, grounding, deterministic success, entity resolution, freshness,
historical acceptance ve provider'ın eklediği yeni sinyaller. Missing evidence ve
contradiction policy penalty'si uygular. Skor 0..1 aralığına alınır.

Çıktı `score`, dinamik `levelConcept`, `requiresReview` ve her sinyal için
`factorConcept/value/weight/effect` içerir. Contradiction her koşulda review'u zorlar.
LLM sonucu varsa model confidence ile policy sonucu birleştirilir; explanation JSON
içinde iki kaynak da korunur.
