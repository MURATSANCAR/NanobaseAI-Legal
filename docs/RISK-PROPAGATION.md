# Risk Propagation

`GraphRiskPropagationEngine`, source risk’in requirement/entity kimliğinden
dependency graph’ı policy depth ve confidence sınırlarıyla gezer. Her aday path,
target entity, propagation concept ve confidence taşır.

Sonuç yeni final risk değildir. `risk_propagation_candidate` kaydı daima
`REVIEW_REQUIRED` olarak açılır ve risk detayında gösterilir. Uzman onayı,
revision ve feedback zinciri olmadan aktif risk/task oluşmaz.

Graph source’ları requirement relation, compliance, shared evidence/capability
adapter’larıyla genişletilebilir; traversal motoru değişmez.
