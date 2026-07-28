# Release Dry Run

Staging dry run adımları:

1. Mevcut sürümü doğrula
2. Backup
3. Upgrade ve migration
4. Smoke + E2E
5. AI sample evaluation
6. Backup validation
7. Rollback ve eski sürüm doğrulama
8. Tekrar upgrade

Dry-run isteği `NOT_RUN` başlar. PASS ancak tamamlanmış step snapshot’ı ve evidence
reference ile ayrıca kaydedilir. İstek atmak başarı sayılmaz.

Bu ortamda Docker/staging olmadığı için dry run çalıştırılmadı ve RC blocker’dır.
