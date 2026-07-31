# Document Capability Profiles

Every document version should obtain a capability profile before heavy processing.

## Model

`document_capability_profile` (V33) stores open-ended concept codes:

- `format_concept_code` — e.g. PDF, DOCX, UNKNOWN
- `content_mode_concept_code` — NATIVE_TEXT, SCANNED_IMAGE, MIXED_TEXT_IMAGE, DOCX_STRUCTURED, TABLE_DOMINANT, …
- `layout_complexity_concept_code` — LOW / MEDIUM / HIGH
- `ocr_need_concept_code` — NONE / OPTIONAL / RECOMMENDED / REQUIRED

Densities, language profile JSON, page/token estimates, and recommended profile codes are persisted for routing telemetry.

## Code

- `DefaultDocumentCapabilityProfiler` — heuristic signals from MIME, text ratio, scan likelihood, table/image counts
- `DefaultDocumentProcessingRouter` — policy map → `DocumentProcessingPlan` (parser/OCR/layout/table/clause chain/budgets)

## Rules

- No institution-specific branching
- Concept lists are not closed enums in Java
- Feature flag: `DOCUMENT_CAPABILITY_PROFILE_ENABLED` (default false)
