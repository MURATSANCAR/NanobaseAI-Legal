# Processing State Machine

Ana yol:

```text
UPLOADED → VIRUS_SCANNING → CLASSIFYING → QUEUED → PARSING
→ [OCR_PROCESSING] → STRUCTURE_DETECTION → INDEXING → READY
```

Terminal durumlar: `READY`, `FAILED`, `MANUAL_REVIEW_REQUIRED`, `CANCELLED`.
`READY → PARSING` yasaktır. Reprocess yeni job üretir. `FAILED → QUEUED` yalnız
retry için geçerlidir. Her geçiş optimistic locked job kaydını, document/version
özet durumunu, `processing_event` ve audit kaydını aynı transaction’da günceller.

Provider job devam ediyorsa consumer retry queue’ya geçer. Cancel edilmiş job
consumer tarafından devam ettirilmez.

