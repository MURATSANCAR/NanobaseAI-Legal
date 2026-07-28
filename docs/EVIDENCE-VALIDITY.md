# Evidence Validity

Validity bir boolean kolon değil, policy-versioned assessment'tir.
`PolicyEvidenceValidityEngine`; bitiş tarihi, parser/OCR kalitesi, verification ve
source-authority sinyallerini aktif policy JSON'undaki ağırlıklarla değerlendirir.
Sonuç selector'ı ontology metadata içindeki `validitySelector` alanına çözülür; Java
kodunda VALID/EXPIRED listesi yoktur.

Knowledge ingestion yeni fragment'ler için `KnowledgeEvidencePolicyService` üzerinden
idempotent assessment oluşturur. Uzman ayrıca verify/invalidate API'leriyle yeni
assessment ekleyebilir. Invalidate, fragment'in `valid_until` değerini sonlandırır ve
audit/outbox üretir.

Retrieval, aktif tarih aralığını ve son assessment'in `usable` metadata'sını dikkate
alır. Positive final decision kontrolü de son assessment kullanılabilir değilse
kararı reddeder.
