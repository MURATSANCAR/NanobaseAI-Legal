# Confidence Policy

Confidence modelin kendi puanı değildir. `WeightedConfidencePolicyEngine` aktif policy
weights ile mevcut factor'leri normalize eder ve her etkinin açıklamasını saklar.

Mevcut factor sözlüğü grounding coverage, schema validation, parser/OCR quality, clause
signal, ontology match ve unit validation girdilerini destekler. Yeni factor kodu
policy/provider verisiyle eklenebilir.

Level kodları ve `reviewBelow` sınırı policy'dedir. Sonuç requirement'ın
`explanation_json.confidence` alanında score, level, input, weight ve effect listesiyle
görülür. Düşük OCR quality ayrı factor olarak güveni düşürür; yüksek güven otomatik
approval sağlamaz.
