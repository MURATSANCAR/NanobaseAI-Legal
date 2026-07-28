# Workflow Condition Engine

`SafeJsonWorkflowConditionEngine`, JSON DSL’i beyaz liste ile değerlendirir. SpEL,
JavaScript, reflection, sınıf erişimi ve metot çağrısı yoktur.

Desteklenen birleşimler `all`, `any`, `not`; yaprak ifade ise `field`, `operator`,
`value` alanlarıdır. Alanlar yalnız context map içindeki nokta yollarından okunur.
Karşılaştırma, eşitlik, koleksiyon üyeliği, varlık ve sayısal operatörler kontrollü
olarak uygulanır.

Koruma sınırları:

- Maksimum expression derinliği ve node sayısı.
- Maksimum field path uzunluğu.
- Bilinmeyen operator fail-closed.
- Eksik alan açık evaluation finding üretir.
- Payload hiçbir zaman executable template’e çevrilmez.

Örnek:

```json
{"all":[
  {"field":"project.criticalRiskCount","operator":"EQUAL","value":0},
  {"field":"project.pendingReviewCount","operator":"EQUAL","value":0}
]}
```

`DynamicWorkflowEnginesTest` güvenli evaluation ve script-benzeri girdinin
reddedilmesini kapsar. Yeni operatörler engine içindeki açık registry/switch
sınırından eklenmelidir.
