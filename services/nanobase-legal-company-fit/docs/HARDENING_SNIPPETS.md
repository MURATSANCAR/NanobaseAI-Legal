# Prod hardening snippets (integrate into platform-backend)

## 1. Outbox publisher — SKIP LOCKED

```sql
-- claim batch
SELECT id, payload, created_at
FROM integration_outbox
WHERE published_at IS NULL
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT :batch;
```

```java
// Pseudocode
@Transactional
public List<OutboxRow> claim(int batch) {
  return jdbc.query("""
      SELECT id, payload FROM integration_outbox
      WHERE published_at IS NULL
      ORDER BY created_at
      FOR UPDATE SKIP LOCKED
      LIMIT ?
      """, batch);
}
```

## 2. API rate limiting (Spring)

```java
// Bucket4j / Filter: per tenant + IP
// POST /api/documents/upload : 30/min/tenant
// POST /api/tenders/*/company-fit : 60/min/tenant
```

Gateway or servlet filter; on exceed → 429 + Retry-After.

## 3. RLS bootstrap (after app sets tenant)

```sql
ALTER TABLE company_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON company_document
  USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
```

App connection: `SET app.tenant_id = '<jwt-tenant>'` per request.

## 4. Worker auth

Prefer mTLS service mesh or short-lived workload identity tokens over long-lived shared secret.
Until then: constant-time compare remains mandatory (already noted in SECURITY-GAPS).
