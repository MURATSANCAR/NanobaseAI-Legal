# Company Fit — integration (wired)

## Locations

| Piece | Path |
|---|---|
| Reference package | `services/nanobase-legal-company-fit/` |
| DI runtime module | `services/document-intelligence/company_fit/` |
| Flyway | `src/main/resources/db/migration/V34__company_capability_fit.sql` |
| Java API | `com.nanobase.specai.companyfit` |

## API (Java gateway — production)

| Method | Path | Body |
|--------|------|------|
| POST | `/api/v1/organizations/{orgId}/capabilities/ingest` | `{ documents: [{ documentId, docType, title, text }] }` |
| GET | `/api/v1/organizations/{orgId}/capabilities` | — |
| POST | `/api/v1/tenders/{documentId}/company-fit` | `{ organizationId?, requirements?, capabilities? }` |
| GET | `/api/v1/tenders/{documentId}/company-fit` | latest reports |

Tenant: `orgId` must equal JWT tenant. If `requirements` omitted, loaded from `requirement` for that document. If `capabilities` omitted, loaded from `company_capability`.

## API (document-intelligence — compute/parity)

| Method | Path |
|--------|------|
| POST | `/v1/organizations/{organization_id}/capabilities/ingest` |
| POST | `/v1/tenders/{document_id}/company-fit` |

## Portal UX (minimal)

1. **Şirket evrakları** — upload list + extracted capability chips  
2. **İhale uygunluk** — overall FIT/CONDITIONAL/NOT_FIT + requirement matrix  
3. Evidence click → source company document

## Policy

- MUST / MANDATORY rows drive overall score  
- No capability invention  
- EXPIRED cert → PARTIAL, not MET  
- Empty company inventory → INSUFFICIENT_DATA  

## Deploy order

1. Flyway **V34** (not V40 — repo sequence after V33)  
2. Rebuild backend + document-intelligence  
3. Portal screens  
4. Hardening: SKIP LOCKED, rate limit (see HARDENING_SNIPPETS.md — RLS already on V34 tables)
