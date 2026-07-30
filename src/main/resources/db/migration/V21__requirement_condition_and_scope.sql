-- Phase 1: Structured requirement conditions (deterministic evaluation input).
-- Existing compliance_condition remains for evaluation expression trees (unused by app writes).

CREATE TABLE requirement_condition (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    requirement_id UUID NOT NULL,
    condition_type VARCHAR(80) NOT NULL DEFAULT 'GENERIC',
    field_name VARCHAR(160),
    operator VARCHAR(40) NOT NULL,
    expected_value TEXT,
    expected_numeric_value NUMERIC(24, 8),
    expected_unit VARCHAR(40),
    expected_date DATE,
    expected_boolean BOOLEAN,
    sequence_no INTEGER NOT NULL DEFAULT 0,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    CHECK (operator IN (
        'EQUALS', 'NOT_EQUALS', 'GREATER_THAN', 'GREATER_THAN_OR_EQUAL',
        'LESS_THAN', 'LESS_THAN_OR_EQUAL', 'CONTAINS', 'NOT_CONTAINS',
        'EXISTS', 'NOT_EXISTS', 'VALID_ON_DATE', 'BEFORE', 'AFTER',
        'IN_SET', 'ALL_OF', 'ANY_OF'
    ))
);

CREATE INDEX ix_requirement_condition_requirement
    ON requirement_condition (organization_id, requirement_id, sequence_no);

CREATE TABLE evidence_scope_declaration (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID,
    company_entity_id UUID,
    document_id UUID,
    scope_type VARCHAR(80) NOT NULL,
    declaration_text TEXT NOT NULL,
    source VARCHAR(40) NOT NULL,
    applies_to_capability_type VARCHAR(80),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (document_id, organization_id)
        REFERENCES document(id, organization_id),
    CHECK (source IN (
        'USER_CONFIRMED', 'DOCUMENT_DECLARATION', 'BUSINESS_RULE', 'EXPERT_CONFIRMED'
    ))
);

CREATE INDEX ix_evidence_scope_org_active
    ON evidence_scope_declaration (organization_id, active, scope_type);
