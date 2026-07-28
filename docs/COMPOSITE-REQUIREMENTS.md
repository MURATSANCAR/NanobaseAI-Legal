# Composite Requirements

Alt koşullar `compliance_condition` tablosunda requirement, parent condition,
condition/logical-operator concept ve `condition_expression_json` ile saklanır.
Operatör adları Java enum'u değildir; ontology metadata/provider seçimine açıktır.

`CompositeConditionEvaluator`, atomic sonuçları `ALL`, `ANY`, `NOT` ve
`AT_LEAST_N` provider davranışlarıyla birleştirir. Her atomic koşul önce seçilen
deterministic strategy veya semantic fallback ile ayrı değerlendirilmelidir. Composite
sonuç tek LLM kararına indirgenmez.

Bu sprintte condition kayıtlarının otomatik modelle ayrıştırılması ayrı bir job olarak
sunulmamıştır; requirement extraction'ın `conditionExpression` çıktısı ve yönetim
verisi üzerinden değerlendirilir. Bu sınırlama bilinen konulara yazılmıştır.
