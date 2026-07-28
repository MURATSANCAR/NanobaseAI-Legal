# Automatic Rollback

`AUTOMATIC_ROLLBACK_DEFAULT` versioned policy aşağıdaki sinyalleri izler:
critical error, authentication failure, cross-tenant alert, migration failure, queue
backlog, model timeout, parser/grounding/report failure spike ve audit integrity.

Tenant verisini etkileyen durumda önce safe stop, sonra rollback esastır. Uygulama
yalnız rollback request kaydeder; gerçek executor sonucu evidence ile ayrı endpoint’e
yazar. Başarısız rollback `ROLLED_BACK` gösterilemez.

Runtime otomatik sinyal/executor entegrasyonu henüz kanıtlanmamıştır.
