# Company Fit — integration

## Package layout

```
domain/models.py
extract/capability_extractor.py
fit/fit_engine.py
api/fit_service.py
db/V40__company_capability_fit.sql
tests/test_company_fit.py
docs/HARDENING_SNIPPETS.md
```

## API

| Method | Path | Body |
|--------|------|------|
| POST | `/v1/organizations/{orgId}/capabilities/ingest` | documents[{documentId, docType, text}] |
| POST | `/v1/tenders/{documentId}/company-fit` | organizationId, requirements[], capabilities?[] |

After company docs pass DI parse, send extracted text to **ingest**.  
After tender requirements READY, call **company-fit** with org capabilities (DB load preferred).

## Portal UX (minimal)

1. **Şirket evrakları** — upload list + extracted capability chips  
2. **İhale uygunluk** — overall FIT/CONDITIONAL/NOT_FIT + requirement matrix  
3. Evidence click → source company document

## Policy

- MUST rows drive overall score  
- No capability invention  
- EXPIRED cert → PARTIAL, not MET  
- Empty company inventory → INSUFFICIENT_DATA  

## Deploy order

1. Flyway V40  
2. Python module on DI/worker image (or Java port of fit_engine)  
3. REST endpoints + tenant filter  
4. Portal screens  
5. Hardening: SKIP LOCKED, rate limit, RLS (see HARDENING_SNIPPETS.md)
