# Capability Modeli

Capability, entity üzerinde sabit bir firma yetkinlik kolonu değildir.
`capability_concept_id` aktif ontology concept'ine, `owner_entity_id` generic entity'ye
bağlanır. Ek özellikler `capability_attributes_json` içinde profile/policy tarafından
yorumlanır.

Bir capability'nin kanıtı `capability_evidence` ile ayrı tutulur. Link; fragment,
dinamik evidence role concept'i, strength ve geçerlilik aralığı taşır. Knowledge
extraction çıktısında owner, capability concept ve en az bir izinli fragment
bulunmadan capability oluşturulmaz. CRUD işlemleri audit üretir; create işlemi
`knowledge.capability.created.v1` outbox event'i yayınlar.
