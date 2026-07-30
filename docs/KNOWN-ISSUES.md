# Known Issues — Compliance Orchestration TX Fix

## Resolved by this change

- Long `@Transactional` on `ComplianceAnalysisProcessor.process` held
  `SELECT … FOR UPDATE` across LLM (~2 min), so polls only saw `QUEUED` until
  `COMPLETED`, cancel blocked, and `now()` timestamps collapsed.

## Remaining / follow-ups

1. Live orchestrated gates (1×1 visibility, cancel, 1×5, timeout, dual-worker,
   reclaim) must be re-run after deploy with V27 applied; results belong in
   `CODEX-HANDOVER-COMPLIANCE-TX-FIX.md`.
2. Task statuses `WAITING_FOR_SLOT` / `CLAIMED` as distinct enum values are not
   fully persisted yet; claim jumps `QUEUED → RUNNING` with lease columns.
3. HTTP client abort of in-flight model calls on cancel is optional and not
   required for cooperative cancel correctness.
4. Historical Sprint 8 issues (Dockerless host limits, etc.) still apply — see
   earlier sections of this file.
5. Intelligence feature flags remain dual-gated and off until orchestration
   acceptance passes.

## Sprint 8 carry-forward

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
