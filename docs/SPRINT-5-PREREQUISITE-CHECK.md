# Sprint 5 Ön Koşul Kontrolü

Kontrol tarihi: 2026-07-28. Sprint 4 altyapısı korunmuş, Sprint 5 profilleri aynı
versiyonlu katalog ve snapshot yaklaşımı üzerine kurulmuştur.

| Bileşen | Kod yolu | Migration | API | Test / çalışan davranış | Eksik / Sprint 5 düzeltmesi |
|---|---|---|---|---|---|
| AnalysisProfile | `analysis/domain/AnalysisProfile.java`, `AnalysisProfileService.java` | V10 `analysis_profile` | `/api/v1/analysis-profiles/*` | Snapshot oluşturma ve tenant sorguları unit testlerde | Knowledge profile, analysis profile referansıyla V11'de eklendi |
| Ontology / OntologyVersion | `JdbcAnalysisCatalog.java`, `AnalysisCatalogController.java` | V10 ontology tabloları | `/api/v1/analysis-catalogs/*` | Aktif tenant/global concept çözümleme | Entity, attribute, relation, capability ve karar kökleri V11'de eklendi |
| TerminologyCatalog | `JdbcAnalysisCatalog.java` | V10 terminology tabloları | analysis catalog API | Tenant/global görünürlük IT kapsamına dahil | `terminology_snapshot` V11'de materyalize edildi |
| PolicyVersion | `PolicyConfiguration.java` ve policy motorları | V10 policy tabloları | analysis catalog API | Ağırlık/eşiklerin JSON'dan okunması testli | Knowledge, validity, authority, matching, comparison ve confidence policy'leri eklendi |
| PromptPackage | `JdbcAnalysisCatalog.java`, orchestrator `app.py` | V10 prompt tabloları | profil snapshot API | JSON-only structured-output çağrısı | Knowledge/compliance prompt package'leri V11'de eklendi |
| OutputSchemaVersion | `DynamicOutputSchemaValidator.java`, `JacksonOutputSchemaValidator.java` | V10 output schema tabloları | profil snapshot API | JSON Schema reddi contract testli | Dinamik knowledge ve evidence-kısıtlı compliance şemaları eklendi |
| Requirement | `analysis/domain/Requirement.java`, `RequirementService.java` | V10 `requirement` | `/api/v1/requirements/*` | CRUD/review/source davranışı mevcut | Compliance task'larının kaynak requirement'ı oldu |
| RequirementRevision | `RequirementRevision.java`, `RequirementService.java` | V10 `requirement_revision` | requirement history/review API | Append-only revision | Değiştirilmedi |
| RequirementSource | `RequirementService.java`, grounding motorları | V10 `requirement_source_fragment` | requirement detail/explanation | Bounding box ve kaynak navigasyonu frontend testli | Evidence fragment ile ayrı fakat kaynak kimliği korunuyor |
| RequirementExtractionJob | `RequirementExtractionJobService.java`, processor/consumer | V10 job/event tabloları | extraction job + SSE | Idempotent consumer ve SSE/polling davranışı | Ortak canlı event stream knowledge/compliance job'larında da kullanıldı |
| ExpertFeedback | `RequirementService.java`, `ComplianceEvaluationService.java` | V10 `expert_feedback`, V11 concept FK | review API'leri | Production'a otomatik aktivasyon yok | Dinamik feedback concept'i ve compliance snapshot'ları eklendi |
| ModelRegistry | `model_definition`, `model_deployment`, `model_profile`; `PolicyModelRoutingEngine.java` | V10 model tabloları | catalog/profile API | Logical model ve fallback profile yapısı | Orchestrator aynı profile ait deployment'larda retry/fallback yapıyor |
| ModelRouting | `ModelRoutingEngine.java`, `PolicyModelRoutingEngine.java` | V10 `model_routing_decision` | job detail | Policy tabanlı seçim | Knowledge/compliance snapshot'larında routing policy saklanıyor |
| GroundingResult | `GroundingValidator.java`, `LayeredGroundingValidator.java` | V10 requirement kaynak/grounding alanları | explanation API | Grounding unit testli | Positive compliance review için grounded evidence zorunlu |
| ConfidenceResult | `WeightedConfidencePolicyEngine.java` | V10 requirement confidence alanları | explanation API | Açıklanabilir faktör testleri | `PolicyComplianceConfidenceEngine` eklendi |
| Requirement Grid Configuration | `UiConfigurationController.java`, frontend requirements modülü | V10 `ui_configuration` | `/api/v1/ui-configurations/requirement-grid` | Kolonların backend'den okunduğu frontend testli | Compliance matrix/entity profile konfigürasyonları eklendi |

Kritik ön koşul açığı bulunmadı. V10'un opaque terminology snapshot UUID kullanımı,
V11'de eski job kayıtlarını bozmadan materyalize snapshot tablosuyla uyumlu hale
getirildi. Testcontainers doğrulaması Docker olmayan bu ortamda çalıştırılamadı.
