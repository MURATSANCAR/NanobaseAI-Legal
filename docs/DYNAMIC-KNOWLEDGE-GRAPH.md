# Dinamik Knowledge Graph

Sprint 5 domain'i firma, ürün, personel, sertifika veya ekipmanı ayrı Java modellerine
kilitlemez. `knowledge_entity`, `entity_attribute`, `knowledge_relation` ve
`capability` kayıtları tenant-scoped UUID ontology concept'leriyle sınıflandırılır.

Kaynaklar:

- Migration: `V11__dynamic_knowledge_compliance.sql`
- Domain DTO'ları: `knowledge/domain/KnowledgeModels.java`
- Uygulama: `KnowledgeGraphService.java`
- API: `KnowledgeController.java`

`entity_type_concept_id`, `attribute_concept_id`, `relation_concept_id` ve
`capability_concept_id` aktif tenant/global ontology concept'i olmalıdır. Yeni sektör
alanları JSON/catalog verisi olarak eklenir; migration, enum veya yeni controller
gerekmez. Entity ve relation geçerliliği `valid_from`/`valid_until` ile, eşzamanlı
güncelleme `version` ile korunur.

Merge/split öncesi snapshot `entity_revision` tablosuna append-only yazılır. Merge,
eski entity'yi sonlandırıp referansları hedefe taşır; split yeni entity üretir ve
kaynak geçmişi kaybetmez. Cross-tenant referanslar composite FK, sorgu filtresi ve RLS
ile engellenir.
