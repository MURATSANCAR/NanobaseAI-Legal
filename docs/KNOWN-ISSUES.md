# Known Issues — Sprint 8

1. Docker engine yok; gerçek infrastructure integration, container build/scan, ClamAV, backup/
   restore, chaos, load, offline ve E2E çalıştırılmadı.
2. Workflow/report/decision/finalization Sprint 7 çalışma ağacı değişiklikleri mevcut; özel
   integration/UAT kanıtı yok.
3. Audit hash chain runtime migration/corruption testi Docker yokluğu nedeniyle koşulmadı.
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

## Sprint 7 özel sınırları

1. V13 migration, FORCE RLS ve repository tenant filtreleri yazıldı; Docker olmayan
   bu hostta gerçek PostgreSQL cross-tenant/RLS entegrasyonu çalıştırılmadı.
2. Sprint 7 notification consumer duplicate teslimata karşı idempotenttir; RabbitMQ
   redelivery/retry/dead-letter ve tüm event zinciri canlı broker ile doğrulanmadı.
3. SLA scheduler warning/breach ve ilk policy escalation’ını üretir; çok seviyeli
   escalation ilerletmesi ile canlı zaman/broker davranışı doğrulanmadı.
4. Clarification answer source-linked analizleri stale işaretler ve yeniden analiz
   event’i üretir; analysis consumer’larının zinciri uçtan uca yenilemesi doğrulanmadı.
5. Report job progress kalıcıdır ancak SSE progress stream’i yoktur. PDF/DOCX/XLSX
   renderer’ları geçerli minimal artifact üretir; ileri branding/accessibility yoktur.
6. Report field-level masking, approval delegation domain modeli ve admin role/
   dashboard editor ekranları tamamlanmadı.
7. Reopen geçmişi append-only korunur; policy ile otomatik yeni workflow instance
   başlatma bağlı değildir.
8. Frontend production build/source tests kapsamındadır; signed-in browser E2E,
   drag/drop workflow canvas ve görsel regression yapılmadı.
