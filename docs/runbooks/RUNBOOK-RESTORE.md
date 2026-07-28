# Restore

**Belirti:** planned drill veya incident recovery. **Neden:** data loss/corruption/environment.
**Kontrol:** approved encrypted archive, manifest, empty staging/DR target, RPO/RTO. **Müdahale:**
`restore-test.sh`; DB→MinIO→Keycloak→app→sentinel UAT→audit chain sırası. **Geri alma:** failed
target’ı izole et; source’a yazma. **Veri riski:** overwrite; production target için change/P1
authority şart. **Eskalasyon:** incident commander, DBA, storage, identity.
