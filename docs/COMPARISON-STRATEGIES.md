# Comparison Strategies

`ComparisonStrategy` port'u `supports(context)` ve `compare(context)` sözleşmesini
taşır. `ComparisonStrategyRegistry`, Spring provider'larını provider code ile seçer;
requirement'taki strategy catalog code, `comparison_strategy_definition` üzerinden
provider'a çözülür.

Mevcut deterministic provider'lar:

- `numeric-threshold`
- `numeric-range`
- `date-validity`
- `boolean-existence`

`manual-only` ve provider bulunamaması semantic evaluation yoluna gider. Numeric
threshold eş birimde doğrudan karşılaştırır; farklı birimde `UnitConversionService`
aktif `measurement_unit.conversion_metadata_json` ile canonical dönüşüm yapar. Birim
switch-case'i yoktur. Her sonuç provider, operand, unit compatibility, deterministic
flag ve reason code taşır.

Yeni strategy eklemek için port implementasyonu ve catalog kaydı yeterlidir; domain
ve controller değişmez. Metrikler `comparison_strategy_total` ve provider tag'idir.
