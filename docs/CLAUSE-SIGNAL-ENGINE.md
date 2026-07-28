# Clause Signal Motoru

`ClauseSignalEvaluator` portu clause'u tek regex kararıyla sınıflandırmaz.
`ClauseSignalFeatureExtractor` şu bağımsız girdileri üretir:

- onaylı tenant/global terminoloji eşleşmesi,
- parser tarafından üretilmiş clause yapısı,
- genel sayısal ifade sinyali.

Motor, policy `signalWeights` anahtarlarını feature map ile eşler. Bilmediği yeni sinyal
adı kodu bozmaz; provider bu sinyali verdiğinde otomatik hesaba katılır. Karar eşikleri
`decisionThresholds.extract` ve `manualReview` alanlarından gelir.

Çıktı `signalScore`, `recommendedAction`, kaynak sinyaller, reason code ve manual review
bilgisini taşır. Mevcut production sağlayıcıları `EXTRACT`, `MANUAL_REVIEW`, `SKIP`
üretir. Parent/child merge veya özel model classifier kararları yeni policy/provider ile
eklenebilir; cümle listesi eklenmez.
