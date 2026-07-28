# Clarification Workflow

Sprint 6 `clarification_candidate` kayıtları Sprint 7 merkezinde review edilebilir
`clarification_request` nesnelerine dönüştürülür. Request kaynak tip/id, öncelik ve
legal/technical review ihtiyaçlarını korur.

Akış sabit sıra değildir. Hedef status concept’i ve metadata action effect’i ile:
revision oluşturma, review’e gönderme, approval, sent işaretleme ve answer alma
işlemleri yapılır. Her metin değişikliği `clarification_revision`, grounding
`clarification_source`, dış cevap ise `clarification_answer` üzerinde immutable
saklanır. Gönderme ancak onaylı revision varsa mümkündür.

Answer alındığında `clarification.answer.received.v1` ve yeniden analiz talebi outbox
event’i oluşturulur. Etkilenen requirement/risk/compliance zincirini seçmek için
source referansları kullanılır.

API `/api/v1/tenders/{projectId}/clarifications` ile merkezi, `/clarifications/{id}`
altındaki action’larla lifecycle’ı sunar. Ayrı RabbitMQ consumer zincirinin canlı
yeniden analiz çalıştırması Docker’sız ortamda doğrulanmamıştır.
