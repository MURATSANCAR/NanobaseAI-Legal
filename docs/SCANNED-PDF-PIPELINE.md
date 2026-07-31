# Scanned PDF Pipeline

Target flow:

```text
Upload → page render → preprocess → OCR → quality → layout → clauses → requirements
```

## Components

- Capability profile marks `SCANNED_IMAGE` / `OCR REQUIRED`
- `OcrPreprocessingProvider` chain (deskew/denoise/… as replaceable providers; passthrough default)
- `ocr_quality_assessment` table for page/block quality status codes (ACCEPT / REPROCESS / ALTERNATIVE_OCR / MANUAL_REVIEW / UNUSABLE) — policy-driven
- Page-scoped retry preferred over full-document reprocess (`preferPageScopedRetry` in plan)

## Status

Architecture + schema ready. Live E2E-02 **PENDING** (licensed scanned fixtures missing).
