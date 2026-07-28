# Known Issues — Sprint 8

1. Docker engine yok; gerçek infrastructure integration, container build/scan, ClamAV, backup/
   restore, chaos, load, offline ve E2E çalıştırılmadı.
2. Workflow/report/decision/finalization Sprint 7 çalışma ağacı değişiklikleri mevcut; özel
   integration/UAT kanıtı yok.
3. Audit hash insert concurrency tenant advisory lock ile sertleştirilmedi.
4. Quota yalnız upload storage/document count’a bağlı; kalan resource kodları bağlanmadı.
5. Backpressure yalnız document upload kabulüne bağlı; model/GPU adapter ve ETA yok.
6. Quality gate evaluator activation service’lerine tam bağlanmadı; shadow/canary execution
   router yalnız şema seviyesinde.
7. MinIO TLS/SSE/versioning/lifecycle/object lock ve least-privilege credential runtime yok.
8. Redis 8, MinIO AGPL, ClamAV GPL ve bütün model/OCR weights exact license legal blocker.
9. Golden/customer pilot dataset yok; AI quality ve UAT tamamlanmadı.
10. Retention/classification schema var; masking/deletion/export enforcement tam değil.
11. GitHub Actions ref’leri commit SHA ile pinlenmedi; Cosign sign/verify yok.
12. Sites portalı için generated social preview kullanıldı; bu runtime security evidence değildir.
