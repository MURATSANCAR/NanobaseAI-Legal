# Release Management v1

Release kontrol düzlemi `release_record`, scope, artifact, immutable configuration
manifest, gate result, approval request, compatibility, dry run, rollout checkpoint,
go-live decision ve stabilization window modellerinden oluşur.

Durum zinciri dış sistem işini uydurmaz:

`DRAFT/SCOPE_LOCKED → GATES_RUNNING → AWAITING_APPROVAL → APPROVED
→ DRY_RUN_REQUESTED → DEPLOYMENT_REQUESTED → DEPLOYED`

Deploy API’si yalnız `DEPLOYMENT_REQUESTED` yazar. `DEPLOYED`, ayrıca runtime evidence
taşıyan deployment result gelince yazılır. Rollback aynı şekilde
`ROLLBACK_REQUESTED → ROLLED_BACK` ayrımını korur.

Artifact digest ve signature referansı zorunludur. Backend/frontend image yalnız
`sha256:<64 hex>` kabul eder.
