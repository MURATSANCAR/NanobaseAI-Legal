# Ontology Yönetimi

Ontology yapısı `ontology`, `ontology_version`, `ontology_concept`,
`ontology_relation_type` ve `ontology_relation` tablolarındadır. Kategori, modalite,
ölçüm ve başka kavram türleri `concept_type` metadatası ile genişler; Java enum değişmez.

Lifecycle durumları `DRAFT -> TESTING -> ACTIVE -> RETIRED` platform yayın sürecidir.
Production extraction yalnız `ACTIVE` sürümü profile snapshot olarak bağlar. Sürüm
değişikliği eski job ve requirement kayıtlarını etkilemez.

Relation type'ları katalog tablosundadır. Yeni bir ilişki türü eklemek migration veya
Java değişikliği gerektirmez. Başlangıçtaki global ontology yalnız genişletilebilir
`REQUIREMENT` kökünü içerir; sektör kategorisi içermez.

Okuma API'leri:

- `GET /api/v1/ontologies`
- `GET /api/v1/ontologies/{id}/versions`
- `GET /api/v1/ontology-versions/{id}/concepts`

Tenant'a özel ontology, aynı tenant transaction'ında global kayıttan daha yüksek
öncelikle çözülür. Aktivasyon yönetici onayı ve evaluation quality gate sonrasında
yapılmalıdır; model önerisi hiçbir kaydı otomatik aktive etmez.
