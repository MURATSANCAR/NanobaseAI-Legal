# Compliance Engine

Compliance job, project access kontrolünden sonra analysis profile, terminology,
ontology, retrieval/matching/comparison/confidence policy, prompt ve model-routing
sürümlerini snapshot eder. Requirement başına idempotent `requirement_matching_task`
oluşturulur.

Processor akışı:

1. Snapshot-scoped aday retrieval.
2. Policy reranking.
3. Candidate yoksa açıklanabilir missing-evidence sonucu.
4. Strategy catalog eşleşirse deterministic karşılaştırma.
5. Çözülemeyen alanlarda yalnız top-K evidence ile local orchestrator semantic çağrısı.
6. Dynamic decision concept çözümü ve confidence.
7. Supporting/contradicting `compliance_evidence_link` kayıtları.
8. Review-required outbox eventi ve job progress/SSE.

Final olumlu karar için `ComplianceEvaluationService` aktif, süre dolmamış,
`usable=true`, grounded ve support-strength > 0 evidence arar. Herhangi bir
contradiction link'i olumlu final kararı bloke eder. Suggested AI kararı final değildir;
uzman review API'si final concept'i, revision, audit ve feedback ile yazar.

Semantic orchestrator request dışı evidence ID, desteklenmeyen decision, grounding
eksikliği ve gizlenen contradiction'ı 422 ile reddeder.
