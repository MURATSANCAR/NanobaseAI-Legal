-- Phase 1: Company capability registry enrichment on existing capability table.

ALTER TABLE capability
    ADD COLUMN IF NOT EXISTS capability_type VARCHAR(80) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(500),
    ADD COLUMN IF NOT EXISTS value_type VARCHAR(40) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS text_value TEXT,
    ADD COLUMN IF NOT EXISTS numeric_value NUMERIC(24, 8),
    ADD COLUMN IF NOT EXISTS unit VARCHAR(40),
    ADD COLUMN IF NOT EXISTS boolean_value BOOLEAN,
    ADD COLUMN IF NOT EXISTS date_value DATE,
    ADD COLUMN IF NOT EXISTS scope_text TEXT,
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(40) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS source_document_id UUID,
    ADD COLUMN IF NOT EXISTS source_evidence_fragment_id UUID,
    ADD COLUMN IF NOT EXISTS verified_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;

UPDATE capability
SET normalized_name = lower(trim(name))
WHERE normalized_name IS NULL;

ALTER TABLE capability
    ALTER COLUMN normalized_name SET NOT NULL;

ALTER TABLE capability DROP CONSTRAINT IF EXISTS ck_capability_type;
ALTER TABLE capability
    ADD CONSTRAINT ck_capability_type CHECK (capability_type IN (
        'CERTIFICATION', 'LICENSE', 'PERSONNEL_COUNT', 'QUALIFIED_PERSONNEL',
        'FINANCIAL_METRIC', 'TURNOVER', 'BALANCE_SHEET', 'CAPITAL',
        'BANK_GUARANTEE_CAPACITY', 'PROJECT_EXPERIENCE', 'REFERENCE_PROJECT',
        'CUSTOMER_REFERENCE', 'TECHNOLOGY', 'INFRASTRUCTURE', 'DATA_CENTER',
        'LOCATION', 'DISTANCE', 'CAPACITY', 'SECURITY_CONTROL', 'INSURANCE',
        'QUALITY_PROCESS', 'LEGAL_DOCUMENT', 'AUTHORIZATION', 'OTHER'
    ));

ALTER TABLE capability DROP CONSTRAINT IF EXISTS ck_capability_value_type;
ALTER TABLE capability
    ADD CONSTRAINT ck_capability_value_type CHECK (value_type IN (
        'TEXT', 'NUMERIC', 'BOOLEAN', 'DATE', 'STRUCTURED'
    ));

ALTER TABLE capability DROP CONSTRAINT IF EXISTS ck_capability_verification_status;
ALTER TABLE capability
    ADD CONSTRAINT ck_capability_verification_status CHECK (verification_status IN (
        'UNVERIFIED', 'AI_EXTRACTED', 'USER_CONFIRMED', 'EXPERT_CONFIRMED',
        'EXPIRED', 'REVOKED'
    ));

-- status remains unconstrained: legacy values include EXTRACTED; v2 uses ACTIVE/EXPIRED/etc.

ALTER TABLE capability DROP CONSTRAINT IF EXISTS fk_capability_source_document;
ALTER TABLE capability
    ADD CONSTRAINT fk_capability_source_document
        FOREIGN KEY (source_document_id, organization_id)
        REFERENCES document(id, organization_id);

ALTER TABLE capability DROP CONSTRAINT IF EXISTS fk_capability_source_fragment;
ALTER TABLE capability
    ADD CONSTRAINT fk_capability_source_fragment
        FOREIGN KEY (source_evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id);

CREATE INDEX IF NOT EXISTS ix_capability_org_type_name
    ON capability (organization_id, capability_type, normalized_name);
CREATE INDEX IF NOT EXISTS ix_capability_org_owner_type
    ON capability (organization_id, owner_entity_id, capability_type);

CREATE TABLE capability_conflict (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    company_entity_id UUID NOT NULL,
    capability_type VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    existing_capability_id UUID NOT NULL,
    incoming_capability_id UUID,
    conflict_summary TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    resolution_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (existing_capability_id, organization_id)
        REFERENCES capability(id, organization_id),
    CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX ix_capability_conflict_open
    ON capability_conflict (organization_id, status, created_at DESC);

CREATE TABLE requirement_capability_match (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    capability_id UUID,
    match_status VARCHAR(40) NOT NULL,
    matched_conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    contradicting_conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence NUMERIC(10, 6) NOT NULL DEFAULT 0,
    evaluation_method VARCHAR(40) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    FOREIGN KEY (capability_id, organization_id)
        REFERENCES capability(id, organization_id),
    CHECK (match_status IN (
        'MATCHED', 'PARTIALLY_MATCHED', 'NOT_MATCHED', 'UNKNOWN', 'EXPIRED', 'OUT_OF_SCOPE'
    )),
    CHECK (confidence BETWEEN 0 AND 1)
);

CREATE INDEX ix_requirement_capability_match_req
    ON requirement_capability_match (organization_id, requirement_id, evaluated_at DESC);
