# Requirement Dependency Graph

`requirement_dependency` source/target requirement, ontology dependency concept,
attributes, confidence, review status ve optimistic version saklar. Relation
etiketleri enum değildir.

Risk/impact graph adapter’ı şu edge’leri birleştirir:

- Clause → Requirement
- Requirement → Requirement
- Requirement → ComplianceEvaluation
- Requirement → Risk

Graph traversal policy `maximumDepth` ve `minimumConfidence` sınırlarına uyar.
Bu graph değişiklik etkisi, evidence invalidation ve risk propagation için ortak
altyapıdır. Reddedilmiş edge’ler kullanılmaz.
