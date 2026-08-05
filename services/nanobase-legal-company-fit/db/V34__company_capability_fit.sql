-- Company capability inventory + tender fit reports (company-fit-v1).
-- Next after V33. Aligns with organization_id tenant model.

CREATE TABLE IF NOT EXISTS company_document (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    doc_type        VARCHAR(64) NOT NULL,
    title           VARCHAR(512),
    storage_key     VARCHAR(1024),
    page_count      INT,
    content_hash    VARCHAR(128),
    source_document_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id, organization_id)
);

CREATE INDEX IF NOT EXISTS ix_company_document_org
    ON company_document (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS company_capability (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organization(id),
    kind                VARCHAR(64) NOT NULL,
    canonical_key       VARCHAR(128) NOT NULL,
    label               VARCHAR(512) NOT NULL,
    attributes_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from          DATE,
    valid_to            DATE,
    validity_status     VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    source_document_id  UUID,
    company_document_id UUID,
    evidence_snippet    VARCHAR(1024),
    confidence          REAL NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id, organization_id),
    FOREIGN KEY (company_document_id, organization_id)
        REFERENCES company_document (id, organization_id),
    CHECK (validity_status IN ('VALID', 'EXPIRED', 'UNKNOWN')),
    CHECK (kind IN (
        'COMPLIANCE_CERT', 'BRAND_AUTH', 'PERSONNEL', 'EXPERIENCE',
        'PRODUCT_SPEC', 'FINANCIAL', 'ADMINISTRATIVE', 'SECURITY_CTRL', 'OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS ix_company_capability_org_key
    ON company_capability (organization_id, canonical_key);

CREATE TABLE IF NOT EXISTS company_fit_report (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organization(id),
    tender_document_id  UUID NOT NULL,
    overall             VARCHAR(32) NOT NULL,
    overall_score       REAL NOT NULL,
    must_met            INT NOT NULL,
    must_total          INT NOT NULL,
    missing_critical    JSONB NOT NULL DEFAULT '[]'::jsonb,
    rows_json           JSONB NOT NULL,
    policy_version      VARCHAR(64) NOT NULL DEFAULT 'company-fit-v1',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (id, organization_id),
    CHECK (overall IN ('FIT', 'CONDITIONAL', 'NOT_FIT', 'INSUFFICIENT_DATA'))
);

CREATE INDEX IF NOT EXISTS ix_company_fit_tender
    ON company_fit_report (organization_id, tender_document_id, created_at DESC);

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['company_document', 'company_capability', 'company_fit_report']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_%I ON %I', t, t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON %I '
            'USING (organization_id = app_current_organization_id()) '
            'WITH CHECK (organization_id = app_current_organization_id())',
            t, t
        );
    END LOOP;
END
$$;
