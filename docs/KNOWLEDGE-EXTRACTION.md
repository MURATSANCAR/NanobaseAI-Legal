# Knowledge Extraction

Knowledge dokümanları requirement prompt'u ile değil,
`knowledge_extraction_profile` snapshot'ı ile işlenir. Snapshot analysis profile,
ontology, terminology, policy, prompt, output schema, model routing, confidence ve
document-role concept sürümlerini sabitler.

Akış:

1. Hazır document version clause'ları tenant filtresiyle alınır.
2. Clause başına content-hash idempotent `evidence_fragment` oluşturulur.
3. Source-authority ve evidence-validity policy'leri materyalize assessment üretir.
4. Orchestrator'a yalnız seçili fragment'ler, aktif ontology ve JSON schema gönderilir.
5. Entity/attribute/relation/capability çıktısındaki her fragment ID request kümesine
   karşı doğrulanır.
6. Bilinmeyen concept `candidate_concept` olur; otomatik aktif edilmez.
7. Job/event sayaçları, audit/outbox ve canlı SSE güncellenir.

İlgili kod: `KnowledgeExtractionJobService`, `KnowledgeExtractionProcessor`,
`HttpKnowledgeAiGateway`, `KnowledgeExtractionConsumer` ve orchestrator
`/v1/knowledge-extractions`.
