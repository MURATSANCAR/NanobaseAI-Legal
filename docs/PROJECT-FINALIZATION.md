# Proje Finalizasyonu

`finalization_policy_version` zorunlu kontrolleri configuration olarak taşır:
workflow tamamlanması, açık task/approval, stale analiz, rapor ve insan executive
decision koşulları. `DecisionAndFinalizationService` policy gate’lerini final kaydı
öncesinde çalıştırır.

`project_finalization` kullanılan policy version, decision case, report artifact,
check snapshot, final status concept, actor ve timestamp’i saklar. Böylece policy
sonradan değişse de karar tekrar üretilebilir.

Reopen yeni bir append-only history kaydı oluşturur; önceki finalizasyonu silmez.
API:

- `POST /api/v1/tenders/{projectId}/finalize`
- `POST /api/v1/tenders/{projectId}/reopen`
- `GET /api/v1/tenders/{projectId}/finalization-history`

Sınırlama: reopen sonrası yeni workflow instance’ını otomatik başlatan policy action
henüz bağlı değildir; reopen event’i yeni akış consumer’ına temel sağlar.
