# RC Scope Lock

`RELEASE_CANDIDATE` oluşturulduğunda mevcut `feature_definition` kayıtları
`release_scope` içine snapshot olarak yazılır ve `scope_locked_at` set edilir.

Scope status concept’leri `IN_SCOPE`, `OUT_OF_SCOPE`, `DEFERRED`, `EXPERIMENTAL` ve
`DISABLED`’dır. RC başladıktan sonra yeni özellik kabul edilmez; yalnız blocker,
security veya rollback düzeltmeleri ayrı audit/release kaydıyla ele alınır.

Bu repo için gerçek RC kaydı oluşturulmamıştır. Kod sprintinin tamamlanması RC scope
kilidinin runtime’da uygulandığı anlamına gelmez.
