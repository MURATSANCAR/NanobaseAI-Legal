# Risk Taxonomy

`risk_taxonomy` global, sektör, organization veya proje kapsamlı kataloğu;
`risk_taxonomy_version` ontology sürümünü ve mapping configuration’ı sabitler.
Risk türleri `ontology_concept` satırlarıdır; Java enum veya kolon constraint’i
değildir.

Çözüm sırası organization kaydını global kaydın önüne alır. Yeni concept,
mevcut ontology’nin yeni onaylı sürümüne veri olarak eklenir. Uygulama değişimi,
migration veya deploy gerekmez.

V12 yalnız güvenli bir bootstrap kökü (`RISK_CANDIDATE`) sağlar. Bu bir kapalı
liste değildir. `conceptMappings` sinyal adı, minimum değer, concept ve reason
code’ları policy verisiyle bağlar; eşleşme yoksa onaylı fallback concept
kullanılabilir.
