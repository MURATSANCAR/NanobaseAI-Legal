# Risk Exposure Policy

`RiskExposurePolicyEngine`, yöntem seçimini `exposureMethod` alanından yapar.
Provider registry şu anda `WEIGHTED_SUM`/`HYBRID` ve `MATRIX` provider’larını
sunmaktadır. Yeni yöntem `RiskExposureMethodProvider` bean’i olarak eklenir;
domain modeli değişmez.

Probability ve impact ayrı ağırlık setleriyle hesaplanır. Exposure birleşimi ve
severity eşikleri aynı immutable snapshot içinde tutulur. Severity sonucu
ontology concept UUID’sidir; LOW/HIGH benzeri enum yoktur.

Her boyut factor code, input, weight ve effect döndürür. Controller score
hesaplamaz; UI bu açıklama kayıtlarını gösterir.

Risk güveni exposure skorundan ayrı hesaplanır.
`ConfigurableRiskConfidencePolicyEngine`, analysis profile içindeki immutable
confidence policy version’ını kullanır; source-confidence, grounding coverage
ve risk-signal girdilerini policy weight/transform tanımlarıyla birleştirir.
Sonuç skoru ve factor açıklamaları `risk_factor` kayıtlarında saklanır.
