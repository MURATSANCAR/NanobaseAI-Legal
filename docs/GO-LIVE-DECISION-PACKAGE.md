# Go-live Decision Package

`GET /api/v1/releases/{id}/go-live-package` şu kaynakları toplar:

- release ve immutable manifest
- gate ve artifact sonuçları
- approval chain
- açık blocker ve accepted quality debt
- dry-run sonuçları
- önceki insan kararları
- missing evidence listesi

GO veya GO_WITH_CONDITIONS yalnız `eligibleForGoLive=true` ise kaydedilebilir.
GO_WITH_CONDITIONS boş koşul kabul etmez. Rollback plan reference her kararda
zorunludur. NO_GO ve REASSESS eksik kanıt varken de insan tarafından kaydedilebilir.

Bu sprint için gerçek paket/veri yoktur; öneri NO-GO’dur.
