# Dinamik Risk Mimarisi

Akış:

```text
Requirement + KnowledgeSnapshot + Compliance
  → immutable RiskAnalysisProfile
  → policy-driven signals
  → ambiguity features
  → staged conflict retrieval
  → deterministic strategy registry
  → exposure/confidence explanation
  → grounded records
  → expert review + revision + audit + feedback
```

`risk/application` yalnız port ve motor sözleşmelerini içerir.
`JdbcRiskCatalog`, tenant/global çözümleme ve aktif sürüm seçimini; persistence
adapter’ları kaynak/revision/job kayıtlarını yürütür. Controller hesaplama yapmaz.

Business değerleri Java enum değildir. Kavram kimlikleri ontology’den,
ağırlık/eşik/yöntemler onaylı policy JSON’undan, UI kolonları
`ui_configuration` içinden gelir. Lifecycle durum string’leri yalnız platform
iş akışıdır.

Güvenlik invariants:

- Kaynaksız risk veya çelişki persist edilmez.
- İki tarafın ID’si request/persisted candidate içinde yoksa conflict reddedilir.
- Critical/final karar insan onaysız oluşmaz; ilk durum `REVIEW_REQUIRED`dır.
- Profile snapshot analiz boyunca değişmez.
- Yeni analiz eski record/revision’ı üzerine yazmaz.
- Bütün store sorguları organization koşulu taşır ve RLS ikinci sınırdır.
