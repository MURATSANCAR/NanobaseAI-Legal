# Clarification Workflow

Sprint 6 `clarification_candidate` kayıtları Sprint 7 merkezinde review edilebilir
`clarification_request` nesnelerine dönüştürülür. Request kaynak tip/id, öncelik ve
legal/technical review ihtiyaçlarını korur.

Akış sabit sıra değildir. Hedef status concept’i ve metadata action effect’i ile:
revision oluşturma, review’e gönderme, approval, sent işaretleme ve answer alma
işlemleri yapılır. Her metin değişikliği `clarification_revision`, grounding
`clarification_source`, dış cevap ise `clarification_answer` üzerinde immutable
saklanır. Gönderme ancak onaylı revision varsa mümkündür.

Answer alındığında `clarification.answer.received.v1` ve
`impact.analysis.requested.v1` outbox event’leri oluşturulur. Source referanslarıyla
etkilenen requirement, compliance evaluation, risk, conflict ve ambiguity kayıtları
bulunur; her biri için idempotent `analysis_staleness_record` açılır ve etkilenen
kayıt sayısı event payload’ına eklenir.

API `/api/v1/tenders/{projectId}/clarifications` ile merkezi, `/clarifications/{id}`
altındaki action’larla lifecycle’ı sunar. Document-intelligence/analysis
consumer’larının stale kayıtları uçtan uca yeniden üretmesi ve canlı broker zinciri
Docker’sız ortamda doğrulanmamıştır.
