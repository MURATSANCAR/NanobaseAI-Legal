# Sprint 7 Ön Koşul Kontrolü

Kontrol tarihi: 2026-07-28. “Test” sütunu kod tabanındaki otomatik kapsamı, “Eksik”
sütunu ise doğrulanmamış veya genişletilmesi gereken alanı gösterir.

| Bileşen | Kod yolu | Migration | API | Test / çalışan davranış | Eksik / Sprint 7 etkisi |
|---|---|---|---|---|---|
| Requirement | `requirement/domain`, `requirement/application` | V10 | `/api/v1/tenders/{id}/requirements` | Revision, source ve explanation akışları testli | Workflow context snapshot’a salt okunur girdi oldu |
| RequirementRevision | `requirement/domain/RequirementRevision.java` | V10 | Requirement detay yanıtı | Revision geçmişi korunur | Ayrı revision liste endpoint’i yok |
| ComplianceEvaluation | `compliance/domain` | V11 | `/compliance-analyses` | Evidence-first değerlendirme testli | Canlı model kalite testi yok |
| ComplianceEvidenceLink | `compliance/domain` | V11 | Compliance detay | Grounded evidence bağlantıları korunur | Alan bazlı hassas veri maskesi tamamlanmadı |
| RiskRecord | `risk/domain`, `risk/application` | V12 | `/risks` | Dinamik risk grid ve karar akışı testli | Gerçek müşteri calibration seti yok |
| ConflictRecord | `risk/domain` | V12 | `/conflicts` | Deterministik karşılaştırma ve review çalışır | Date/range provider genişletilebilir |
| AmbiguityFinding | `risk/domain` | V12 | `/ambiguities` | Clarification candidate üretebilir | Canlı semantic provider doğrulanmadı |
| ClarificationCandidate | `risk/domain` | V12 | Clarification center ile okunur | Sprint 7’de `clarification_request` ve revision’a dönüştürülür | Uçtan uca broker zinciri Docker’sız doğrulanmadı |
| MitigationCandidate | `risk/domain` | V12 | Risk review API | İnsan incelemesi saklanır | Tenant katalogları müşteri verisi gerektirir |
| ImpactAnalysis | `risk/application` | V12 | `/impact-analyses` | Change-set etkisi ve staleness üretir | Büyük veri performansı ölçülmedi |
| AnalysisStalenessRecord | `risk/domain` | V12 | Analiz/detail cevapları | Report data policy stale veriyi gate eder | PostgreSQL entegrasyonu bu hostta skip |
| ExpertFeedback | `analysis/domain` | V10 | Analysis feedback API | Auditli feedback persistence | Gerçek evaluation feedback döngüsü yok |
| KnowledgeSnapshot | `knowledge/domain` | V11 | Knowledge API | Snapshot referansları immutable kullanılır | pgvector provider bağlı değil |
| AnalysisProfile | `analysis/domain` | V10 | Profile UI configuration | Provider/policy seçimi dinamik | Tenant bazlı admin edit UI sınırlı |
| PolicyVersion | `analysis/domain` | V10 | Policy configuration API | Versiyon referansı model run’a yazılır | Production approval süreci harici yönetişim ister |
| PromptPackageVersion | `analysis/domain` | V10 | Prompt package API | Aktif sürüm ve audit korunur | Canlı model benchmark yok |
| ModelRun | `analysis/domain` | V10 | Analysis job/detail API | Model/prompt/policy provenance saklanır | Lokal deployment yapılandırılması gerekir |
| AuditEvent | `audit/domain`, `shared/audit` | V1 | Operasyon/audit uçları | Append-only hash integrity doğrulaması var | Keycloak login event’leri DB audit’e kopyalanmıyor |

Sprint 7 düzeltmesi; bu nesneleri kopyalamak yerine `report_data_snapshot`,
`workflow_instance.context_snapshot_json`, karar faktörü kaynak referansı ve
clarification source ile immutable referanslamaktır. Kritik bir ön koşul eksikliği
bulunmadı. Canlı PostgreSQL/RLS doğrulaması Docker olmayan bu hostta yapılamadı.
