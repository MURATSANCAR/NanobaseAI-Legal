# Parser Routing – pdf-inspector Integration (Production)

**Status:** Ready for merge  
**Date:** 2026-08-04  
**Owner:** document-intelligence  

## Goal

Close the architecture gap “OCR/Docling ve parser routing” by inserting a **fast, local, zero-OCR** classifier in front of the existing Docling / OpenContracts path.

## Decision Table (updated)

| Signal | Decision | Provider | OCR Mode | Notes |
|---|---|---|---|---|
| pdf-inspector → `text_based` (conf ≥ 0.55) | Fast path | Docling (native) **or** direct Markdown | DISABLED | Skip heavy page render; use inspector Markdown when available |
| pdf-inspector → `mixed` | Hybrid | Docling | AUTO / selective | Overlay `pages_needing_ocr` onto PageCapability |
| pdf-inspector → `scanned` / `image_based` | Full OCR | Docling | FORCED | Existing scanned pipeline unchanged |
| pdf-inspector unavailable / timeout / low confidence | Legacy | pypdf + Docling | AUTO | Graceful degradation, no job failure |
| DOCX | Unchanged | Docling | DISABLED | pdf-inspector is PDF-only |
| Annotation sync requested | Unchanged | OpenContracts | AUTO | Existing priority preserved |

## Configuration

```bash
# .env / Compose
PDF_INSPECTOR_ENABLED=true
PDF_INSPECTOR_TIMEOUT_SECONDS=8          # hard ceiling per document
PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT=120
PDF_INSPECTOR_MIN_CONFIDENCE=0.55
```

## Observability

New metrics / events:

- `document_parser_route_total{provider="pdf_inspector", decision="fast|hybrid|fallback"}`
- Processing event stage `pdf_inspector` with `pdf_type`, `confidence`, `duration_ms`, `pages_needing_ocr`
- Readiness probe key `pdf_inspector` (`library_loaded`, `enabled`)

## Failure modes (all non-fatal)

| Failure | Behaviour |
|---|---|
| Package not installed | Log warning once, fall back to legacy classifier |
| Timeout (> 8 s) | Return unknown + continue with pypdf |
| Crash inside native code | Caught, error string recorded, fall back |
| Low confidence | Treat as unknown, fall back |

## Deployment notes

1. Add `pdf-inspector>=0.1.0` to `services/document-intelligence/requirements.txt`
2. Rebuild the document-intelligence image (Rust wheel is platform-specific; build on the same architecture as the target node)
3. Set the four env vars above
4. Verify `/health/ready` returns `"pdf_inspector": {"library_loaded": true}` (when package installed)
5. Run a known digital PDF and a known scanned PDF; confirm the route events

## Rollback

Set `PDF_INSPECTOR_ENABLED=false`. No schema change, no data migration. Instant.

## Future

- Feed the high-quality Markdown directly into the clause extractor for pure `text_based` documents (further latency win).
- Expose per-page confidence scores once the upstream library stabilises the API.
