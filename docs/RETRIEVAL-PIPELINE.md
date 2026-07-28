# Retrieval Pipeline

`EvidenceCandidateRetriever` port'u bütün knowledge graph'ı LLM'e göndermez.
`JdbcEvidenceCandidateRetriever` aşağıdaki sıralı daraltmayı uygular:

1. Tenant + knowledge snapshot zaman cutoff'u.
2. Aktif fragment/entity ve opsiyonel hedef entity scope'u.
3. Attribute, claim veya capability provenance varlığı.
4. Requirement primary/attribute concept eşleşmesi.
5. PostgreSQL full-text lexical score ve typed numeric/boolean/date alanları.
6. Bir adımlık relation graph expansion.
7. Son kullanılabilir validity assessment ve source-authority score.
8. Historical approved feedback sinyali.
9. Policy `candidateLimits.metadata` sınırı.
10. Çok sinyalli reranking + top-K.

Policy sürümü job/snapshot üzerinde saklanır. Embedding için ayrı bir üretim provider'ı
bu sprintte bağlanmamıştır; mevcut implementasyon PostgreSQL full-text + ontology +
graph fallback'ı kullanır. Pgvector provider boşluğu `KNOWN-ISSUES.md` içinde açıktır.

Metrikler: `retrieval_candidate_total` ve `retrieval_duration_seconds`.
