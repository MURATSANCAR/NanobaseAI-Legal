# Restore Test Results

Durum: **NOT_VERIFIED**. `scripts/restore-test.sh` yalnız `DEPLOYMENT_PROFILE=staging` ve explicit
work directory ile çalışır; decrypt+manifest check, PostgreSQL restore, MinIO mirror ve health/
project/audit smoke adımlarını otomatikleştirir.

Keycloak import, belirli proje/doküman/requirement/report download sentinel’ları ve audit chain
karşılaştırması script’e deployment-specific test data geldiğinde eklenmelidir. Ölçülmüş restore
süresi yoktur; production backup kabul edilmez.
