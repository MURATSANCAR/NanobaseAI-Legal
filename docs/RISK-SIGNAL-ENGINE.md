# Risk Signal Engine

`RiskSignalEngine` girdisi tenant/proje/source kimliği, normalize 0–1 sinyaller
ve metadata’dır. `ConfigurableRiskSignalEngine` yalnız policy’de tanımlı
`weights`, `conceptMappings` ve `detailedAnalysisThreshold` alanlarını işler.

Raw değerlerin dönüşümü de `signalSources` configuration’ındadır. Örneğin
grounding coverage doğrudan risk kararı değildir; policy `ONE_MINUS` dönüşümüyle
grounding gap üretir. Evidence, confidence ve testability aynı mekanizmayı
kullanır.

Çıktı:

- normalize signal score,
- açıklanabilir factor/input/weight/effect listesi,
- sıralı concept adayları ve reason code’ları,
- detailed analysis kararı.

Regex veya tek başına LLM final risk üretmez. Unit testler tenant’a özgü yeni
concept ve ağırlıkların kod değişmeden çalıştığını doğrular.
