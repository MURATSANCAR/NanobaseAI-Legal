-- Phase 2: Gap analysis, remediation, assessment enrichment columns.

ALTER TABLE compliance_evaluation
    ADD COLUMN IF NOT EXISTS assessment_status VARCHAR(40) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS evaluation_source VARCHAR(40) NOT NULL DEFAULT 'HYBRID',
    ADD COLUMN IF NOT EXISTS candidate_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS missing_requirement_elements_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS explicit_contradiction BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS closed_world_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reasoning_summary TEXT,
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

ALTER TABLE compliance_evaluation DROP CONSTRAINT IF EXISTS ck_compliance_assessment_status;
ALTER TABLE compliance_evaluation
    ADD CONSTRAINT ck_compliance_assessment_status CHECK (assessment_status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'REVIEW_REQUIRED', 'OVERRIDDEN'
    ));

ALTER TABLE compliance_evaluation DROP CONSTRAINT IF EXISTS ck_compliance_evaluation_source;
ALTER TABLE compliance_evaluation
    ADD CONSTRAINT ck_compliance_evaluation_source CHECK (evaluation_source IN (
        'DETERMINISTIC', 'LLM', 'HYBRID', 'MANUAL'
    ));

CREATE TABLE compliance_gap (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    assessment_id UUID,
    gap_type VARCHAR(60) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    remediability VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    missing_elements_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommended_action TEXT,
    estimated_resolution_days INTEGER,
    estimated_cost NUMERIC(18, 2),
    currency VARCHAR(3),
    action_deadline DATE,
    owner_department VARCHAR(120),
    owner_user_id VARCHAR(255),
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    FOREIGN KEY (assessment_id, organization_id)
        REFERENCES compliance_evaluation(id, organization_id),
    CHECK (gap_type IN (
        'MISSING_CAPABILITY', 'EXPIRED_DOCUMENT', 'INSUFFICIENT_EVIDENCE',
        'NUMERIC_SHORTFALL', 'EXPERIENCE_SHORTFALL', 'PERSONNEL_SHORTFALL',
        'FINANCIAL_SHORTFALL', 'SCOPE_MISMATCH', 'DOCUMENT_MISSING',
        'AMBIGUOUS_REQUIREMENT', 'CONTRACTUAL_RISK', 'OTHER'
    )),
    CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CHECK (status IN (
        'OPEN', 'PLANNED', 'IN_PROGRESS', 'RESOLVED', 'ACCEPTED_RISK', 'WAIVED',
        'NOT_APPLICABLE'
    ))
);

CREATE INDEX ix_compliance_gap_project_status
    ON compliance_gap (organization_id, project_id, status, severity);

CREATE TABLE remediation_action (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    gap_id UUID NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    description TEXT NOT NULL,
    responsible_department VARCHAR(120),
    responsible_user_id VARCHAR(255),
    planned_start_date DATE,
    due_date DATE,
    estimated_cost NUMERIC(18, 2),
    currency VARCHAR(3),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    completion_evidence_document_id UUID,
    completion_evidence_fragment_id UUID,
    completed_at TIMESTAMPTZ,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (gap_id, organization_id)
        REFERENCES compliance_gap(id, organization_id),
    CHECK (action_type IN (
        'OBTAIN_CERTIFICATE', 'HIRE_PERSONNEL', 'ASSIGN_PERSONNEL', 'PROCURE_CAPACITY',
        'UPDATE_DOCUMENT', 'REQUEST_CLARIFICATION', 'FORM_PARTNERSHIP', 'USE_SUBCONTRACTOR',
        'PROVIDE_REFERENCE', 'OBTAIN_GUARANTEE', 'MANAGEMENT_ACCEPTANCE', 'OTHER'
    )),
    CHECK (status IN (
        'DRAFT', 'PROPOSED', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REJECTED'
    ))
);

CREATE INDEX ix_remediation_action_gap
    ON remediation_action (organization_id, gap_id, status);

CREATE TABLE remediability_lead_time (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    capability_type VARCHAR(80) NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    average_days INTEGER NOT NULL,
    approval_days INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, capability_type, action_type)
);

INSERT INTO remediability_lead_time (
    id, organization_id, capability_type, action_type, average_days, approval_days, notes,
    created_at, updated_at
) VALUES
    ('83000000-0000-0000-0000-000000000001', NULL, 'CERTIFICATION', 'OBTAIN_CERTIFICATE',
     90, 10, 'ISO-class certification acquisition', now(), now()),
    ('83000000-0000-0000-0000-000000000002', NULL, 'PERSONNEL_COUNT', 'HIRE_PERSONNEL',
     30, 5, 'Average technical hire lead time', now(), now()),
    ('83000000-0000-0000-0000-000000000003', NULL, 'QUALIFIED_PERSONNEL', 'ASSIGN_PERSONNEL',
     7, 2, 'Internal reassignment', now(), now()),
    ('83000000-0000-0000-0000-000000000004', NULL, 'LEGAL_DOCUMENT', 'UPDATE_DOCUMENT',
     5, 1, 'Document refresh', now(), now())
ON CONFLICT DO NOTHING;
