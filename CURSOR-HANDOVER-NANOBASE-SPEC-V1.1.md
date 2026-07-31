# CURSOR handover — Nanobase Spec Intelligence v1.1

## 1. v1.0 baseline doğrulaması

- `compliance-orchestration-v1.0` → `e9a44f1` intact
- `nanobase-spec-intelligence-v1.0` → `10a1cad` intact
- Live DSİ IDs remain the v1.0 production evidence (see `docs/FULL-PRODUCT-PRODUCTION-READINESS.md`)

## 2. Değiştirilen bileşenler

Additive only: V33 schema, capability/OCR/table/delivery/validity/timing/guardrail/report-visual packages, corpus manifests, docs.

Runtime wiring (feature-flag default OFF):
- `DocumentUploadedConsumer` → `DocumentV11EnrichmentService` (capability/OCR/table cells/DOCX blocks)
- `DocumentService.downloadUrl` → `ObjectDeliveryStrategy` when `OBJECT_DELIVERY_STRATEGY_ENABLED`
- `RequirementExtractionProcessor` → stage timing when `REQUIREMENT_EXTRACTION_TIMING_ENABLED`
- `DynamicReportingService` → visual structure gate when `REPORT_VISUAL_VALIDATION_ENABLED`

Compliance lease/fencing/Redis untouched.

## 3. Document capability profile

`document_capability_profile` + `DefaultDocumentCapabilityProfiler` + `DefaultDocumentProcessingRouter`.

## 4. Scanned PDF pipeline

Profile routing + OCR preprocess port + quality table. Live E2E PENDING.

## 5. OCR quality ve numeric integrity

`ocr_quality_assessment` + `DefaultNumericOcrIntegrityValidator` (no silent correction).

## 6. DOCX pipeline

`docx_structure_block` schema + flags. Live E2E PENDING.

## 7. Table canonical model

`document_table` extensions + `document_table_cell` + `CanonicalTable*`.

## 8. Table requirement extraction

`HeaderContextTableRequirementExtractionStrategy` preserves header context.

## 9–12. Knowledge corpus / certificate / datasheet / validity

Corpus tables seeded; `DeterministicKnowledgeValidityEvaluator`; live knowledge E2Es PENDING.

## 13–14. Corpus ve slice evaluation

15 fixture manifests; harness discovery SKIPPED without binaries; slice metrics policy documented.

## 15–16. Report regression / visual validation

`ReportVisualStructureValidator` + `report_visual_validation_result`. Live E2E-07 PENDING.

## 17–18. Presigned / proxy-only

`ProfileAwareObjectDeliveryStrategy`; default `BACKEND_PROXY_ONLY`; blocks Docker-internal hosts.

## 19–21. Requirement performance

Timing table + budget profiles + optimization policy docs. Live budget PASS not claimed.

## 22. Database migrations

`V33__spec_intelligence_v11_foundations.sql` only (no V28–V32 edits).

## 23–26. Tests

Unit/architecture tests added for profiler/router/numeric/table/validity/delivery/v11 architecture. Host JDK may be unavailable in agent shell — verify via Docker backend build.

Security/load live suites: PENDING.

## 27–32. Live E2Es

Scanned / DOCX / table / certificate / datasheet / report regression: **PENDING** (no invented fixtures).

## 33. DSİ v1.0 regression

Baseline preserved; re-run `scripts/full_product_e2e_autonomous_dsi.py` after enabling V33 on the stack.

## 34. Başarısız / çalıştırılmamış

All broad GA live gates PENDING — intentionally not marked PASS.

## 35. Kalan riskler

Corpus licensing, OCR engine wiring, DOCX structure populate path, extraction mega-transaction, public MinIO DNS, performance wall-clock.

## 36. BROAD_DOCUMENT_GA_READY kararı

```text
BROAD_DOCUMENT_GA_READY = false
```

Do **not** create `nanobase-spec-intelligence-v1.1` tag until live gates pass.
