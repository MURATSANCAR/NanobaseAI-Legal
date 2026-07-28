# v1.0 RC Known Issues

## Blocker

1. PostgreSQL V15 migration ve RLS integration Docker olmadığı için runtime’da
   çalıştırılmadı.
2. Real-stack Playwright E2E çalışmadı.
3. Load/large document/chaos testleri çalışmadı.
4. Backup, restore, RPO/RTO ölçümü yok.
5. Offline install, upgrade ve rollback dry run yok.
6. Müşteri golden dataset, gerçek model evaluation ve AI quality gate yok.
7. UAT 0/15 ve müşteri sign-off yok.
8. SBOM, final license report, image signature/digest artifact yok.
9. Pentest, DAST, gerçek ClamAV/EICAR, Keycloak/MFA ve MinIO security runtime yok.
10. Shadow/canary worker execution ve automatic rollback executor runtime kanıtı yok.

## Non-blocker geliştirme notu

İlk AI orchestrator pytest koşumu izole ortamda `jsonschema` eksikliği nedeniyle
collection’da durdu. Requirements kurulduktan sonra aynı suite 17/17 geçti.

## Karar

Known issues açıkken v1.0 RC/GA artifact veya production GO verilemez.
