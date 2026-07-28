# Sprint 6 Ön Koşul Doğrulaması

Doğrulama tarihi: 2026-07-28. Sprint 4 gereksinim analizi temeli mevcuttu.
İlk kontrolde Sprint 5 knowledge/evidence/compliance katmanı eksikti; bu kritik
ön koşul `V11__dynamic_knowledge_compliance.sql` ve ilgili `knowledge` /
`compliance` modülleriyle tamamlandı. Aşağıdaki yollar çalışan son durumu gösterir.

| Bileşen | Kod yolu | Migration | API | Test | Çalışan davranış | Eksik alan / Sprint 6 düzeltmesi |
|---|---|---|---|---|---|---|
| AnalysisProfile | `analysis/domain/AnalysisProfile.java` | V10 | `/api/v1/analysis-profiles/preview` | `DynamicAnalysisEnginesTest` | Ontology, policy, prompt ve model snapshot’ı immutable saklanır | Risk için ayrı `risk_analysis_profile` eklendi |
| OntologyVersion | `analysis/infrastructure/JdbcAnalysisCatalog.java` | V10 | `/api/v1/ontologies/{id}/versions` | `PlatformInfrastructureIT` | Aktif global/tenant sürümü çözülür | Risk/change kavramları aynı katalogdan çözülür |
| TerminologySnapshot | `AnalysisPersistenceStore.terminologySnapshot` | V11 | İç servis; profile üzerinden görünür | Migration/compile doğrulaması | Extraction öncesi materialize edilir | Önceden opaque UUID idi; fiziksel snapshot yazımı eklendi |
| PolicyVersion | `AnalysisCatalogPort` / `RiskCatalogPort` | V10, V11, V12 | Katalog ve profile API’leri | Dinamik motor testleri | Davranış JSON policy’den gelir | Risk/conflict/ambiguity/impact policy türleri eklendi |
| PromptPackageVersion | `AnalysisCatalogPort.prompt` | V10 | Analysis profile çıktısı | Extraction testleri | Component + output schema sürümü saklanır | Risk profiline referans eklendi |
| OutputSchemaVersion | `DynamicOutputSchemaValidator` | V10 | Analysis catalog | Schema testleri | JSON Schema runtime’da doğrulanır | Governed AI sözleşmelerinde tekrar doğrulanır |
| Requirement | `analysis/domain/Requirement.java` | V10 | `/api/v1/tenders/{id}/requirements` | `DynamicAnalysisEnginesTest` | Grounded, sürümlü requirement | Risk/change graph girdisi yapıldı |
| RequirementRevision | `RequirementRevision.java` | V10 | `/api/v1/requirements/{id}/revisions` | Requirement servis testleri | Eski snapshot korunur | Impact graph revision’ı silmez |
| RequirementSource | `requirement_source_fragment` / `RequirementService` | V10 | Requirement explanation | Grounding testleri | Metin, sayfa ve bbox bağlıdır | Risk/ambiguity/conflict source’a taşınır |
| KnowledgeEntity | `knowledge/application/KnowledgeGraphService.java` | V11 | `/api/v1/knowledge/entities` | Knowledge servis testleri | Dinamik entity concept + attribute | Risk dependency graph tüketicisi yapıldı |
| EntityAttribute | `knowledge/domain/KnowledgeModels.java` | V11 | Knowledge entity detayları | Dynamic value testleri | JSON değer/unit/source saklanır | Conflict structured-value girdisi |
| KnowledgeRelation | `KnowledgeGraphService.java` | V11 | Knowledge relation API | Knowledge testleri | Tenant scoped graph edge | Propagation için graph kaynağı |
| Capability | `KnowledgeGraphService.java` | V11 | Capability API | Knowledge testleri | Ontology concept tabanlı yetenek | Risk sinyali olarak kullanılabilir |
| EvidenceFragment | `knowledge/application/EvidenceService.java` | V11 | `/api/v1/evidence/fragments` | Evidence testleri | PDF sayfa/bbox ve kalite saklanır | Risk/conflict source FK eklendi |
| EvidenceClaim | `EvidenceService.java` | V11 | Evidence claim API | Evidence testleri | Entity/concept/value claim’i grounded saklanır | Conflict candidate türü desteklenir |
| EvidenceValidityAssessment | `EvidenceService.java` | V11 | Evidence validity API | Evidence testleri | Sürümlü policy ile geçerlilik | Risk signal evidence-gap hesabına bağlandı |
| KnowledgeSnapshot | `KnowledgeExtractionProcessor.java` | V11 | Extraction job sonucu | Knowledge integration testleri | Entity/relation/evidence set snapshot’ı | Risk profile ve job’a bağlandı |
| ComplianceEvaluation | `compliance/application/ComplianceEvaluationService.java` | V11 | `/api/v1/compliance-evaluations` | Compliance strategy testleri | Deterministik + model destekli değerlendirme | Impact/staleness graph’a bağlandı |
| ComplianceEvidenceLink | `ComplianceEvaluationService.java` | V11 | Evaluation detayları | Compliance testleri | Supporting/contradicting evidence seçimi saklanır | Evidence gap sinyaline bağlandı |
| ExpertFeedback | `AnalysisPersistenceStore.feedback` | V10 | Review endpoint’leri | Requirement/risk review testleri | Düzeltme ve learning onayı ayrı saklanır | Risk, ambiguity ve conflict review’a bağlandı |
| ModelRun | `AnalysisPersistenceStore` / AI gateways | V10, V11 | Job explanation | AI contract testleri | Prompt/model/schema sürümü ve token/latency | Risk task modeli FK ile hazır; model çağrısı yalnız seçilmiş kaynak alır |

## Sonuç

Kritik ön koşullar kod, migration ve API düzeyinde mevcuttur. Docker daemon
olmadığı için bu çalışma ortamında Testcontainers/RLS entegrasyon turu
çalıştırılamadı; migration ve RLS testi korunmuş, `PlatformInfrastructureIT`
beklenen Sprint 5–6 tabloları ve risk kuyruğunu kapsayacak şekilde genişletilmiştir.
