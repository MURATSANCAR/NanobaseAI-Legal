-- Company capability inventory + tender fit reports
-- Flyway-style; adjust schema names to platform conventions.

CREATE TABLE IF NOT EXISTS company_document (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    organization_id UUID NOT NULL,
    doc_type        VARCHAR(64) NOT NULL,
    title           VARCHAR(512),
    storage_key     VARCHAR(1024),
    page_count      INT,
    content_hash    VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_company_document_org
    ON company_document (tenant_id, organization_id);

CREATE TABLE IF NOT EXISTS company_capability (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    organization_id     UUID NOT NULL,
    kind                VARCHAR(64) NOT NULL,
    canonical_key       VARCHAR(128) NOT NULL,
    label               VARCHAR(512) NOT NULL,
    attributes_json     JSONB NOT NULL DEFAULT '{}',
    valid_from          DATE,
    valid_to            DATE,
    validity_status     VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    source_document_id  UUID REFERENCES company_document(id),
    evidence_snippet    VARCHAR(1024),
    confidence          REAL NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_company_capability_org_key
    ON company_capability (tenant_id, organization_id, canonical_key);

CREATE TABLE IF NOT EXISTS company_fit_report (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    organization_id     UUID NOT NULL,
    tender_document_id  UUID NOT NULL,
    overall             VARCHAR(32) NOT NULL,
    overall_score       REAL NOT NULL,
    must_met            INT NOT NULL,
    must_total          INT NOT NULL,
    missing_critical    JSONB NOT NULL DEFAULT '[]',
    rows_json           JSONB NOT NULL,
    policy_version      VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_company_fit_tender
    ON company_fit_report (tenant_id, tender_document_id, created_at DESC);

-- Optional RLS (enable in prod when JWT sets app.tenant_id)
-- ALTER TABLE company_document ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY company_document_tenant ON company_document
--   USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
