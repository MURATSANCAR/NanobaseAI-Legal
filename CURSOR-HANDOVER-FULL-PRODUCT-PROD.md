# CURSOR handover — Full Product DI / Download / Report

## Locked baseline (do not touch)

- Tag: `compliance-orchestration-v1.0` @ `e9a44f1`
- Prepare `REQUIRES_NEW` / Execute `NEEDS_NEW` wait — Execute `NEVER` / Persist `REQUIRES_NEW`
- Lease fencing, Redis capacity, FAIL_CLOSED, Hikari guardrails, semantic policy hash `65f7982cf7b27f34433cae2f9a5f8eee`

## What shipped in this effort

### Schema
- V29 `document_layout_block`, `recurring_page_element`, clause segmentation columns
- V30 `clause_chunk`, requirement empty-outcome counters
- V31 `knowledge_extraction_run_stage`, purpose / stage / reuse columns
- V32 `report_validation_result`, `document_access_url_audit`

### Document intelligence
- `ClauseSegmentationProvider` chain: DoclingStructure → TextHierarchy → ObligationAwareFallback
- `ClauseSegmentationService.enrich` → `EnrichedExtraction` with layout/recurring drafts
- Persist drafts in `DocumentExtractionPersistenceService`
- Docling Python page fallback when heading-based clauses empty

### Requirements
- `ClauseChunker` bounds LLM input
- `EmptyExtractionOutcome` taxonomy; suspicious empty → partial completion + events
- Persist chunks + job counters

### Storage / download
- Internal `MINIO_ENDPOINT` + public `MINIO_PUBLIC_ENDPOINT`
- Presign via public client; reject Docker-only hosts when endpoints differ
- Streaming proxy: `GET /api/v1/documents/{id}/download`, `GET /api/v1/report-artifacts/{id}/download`
- Compose EasyMeeting defaults public endpoint to `http://127.0.0.1:9000`

### Reports
- `ReportIntegrityValidator` before `REPORT_JOB_COMPLETED`
- Multi-page PDF renderer with project/requirements/compliance/risks section text

### Knowledge
- Stage telemetry PREPARE / AI_EXTRACT / PERSIST
- Purpose CERTIFICATE vs TENDER_SPEC fragment caps + reuse path

### Gates / UI / tests
- `scripts/full_product_e2e_autonomous_dsi.py` — zero SQL seed
- Requirement review actions in UI matrix
- Unit + `FullProductArchitectureTest`
- Decision: `docs/FULL-PRODUCT-PRODUCTION-READINESS.md` (currently **false**)

## Verify locally

```bash
mvn -Dtest=ClauseChunkerTest,EmptyExtractionOutcomeTest,DefaultReportIntegrityValidatorTest,ObligationAwareFallbackProviderTest,FullProductArchitectureTest test
python3 scripts/full_product_e2e_autonomous_dsi.py
```

## Next agent priorities

1. Deploy build + run autonomous DSİ E2E; paste live IDs into readiness doc.
2. Evidence-doc (CERTIFICATE) E2E-05 under `testdata/corpus/`.
3. Complete corpus E2E-02..07 scorers against versioned policy.
4. Expand clause edit/split/merge UI beyond approve/reject if product requires.
