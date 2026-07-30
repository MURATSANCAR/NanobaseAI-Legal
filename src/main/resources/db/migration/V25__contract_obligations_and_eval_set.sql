-- Phase 4: Contract obligation management after award.

CREATE TABLE contract_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    source_document_id UUID,
    title VARCHAR(500) NOT NULL,
    contract_number VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    start_date DATE,
    end_date DATE,
    currency VARCHAR(3),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (source_document_id, organization_id)
        REFERENCES document(id, organization_id),
    CHECK (status IN (
        'DRAFT', 'ACTIVE', 'COMPLETED', 'TERMINATED', 'CANCELLED'
    ))
);

CREATE TABLE contract_obligation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    source_requirement_id UUID,
    source_document_id UUID,
    obligation_type VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    responsible_department VARCHAR(120),
    responsible_user_id VARCHAR(255),
    start_date DATE,
    due_date DATE,
    recurrence_rule VARCHAR(500),
    status VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    criticality VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    evidence_required BOOLEAN NOT NULL DEFAULT TRUE,
    acceptance_criteria TEXT,
    penalty_description TEXT,
    penalty_amount NUMERIC(18, 2),
    currency VARCHAR(3),
    completed_at TIMESTAMPTZ,
    verified_by VARCHAR(255),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (contract_id, organization_id)
        REFERENCES contract_record(id, organization_id),
    FOREIGN KEY (source_requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    FOREIGN KEY (source_document_id, organization_id)
        REFERENCES document(id, organization_id),
    CHECK (obligation_type IN (
        'DELIVERABLE', 'MILESTONE', 'REPORTING', 'SLA', 'CERTIFICATION', 'PERSONNEL',
        'INFRASTRUCTURE', 'SECURITY', 'PAYMENT', 'GUARANTEE', 'INSURANCE', 'AUDIT',
        'MAINTENANCE', 'RENEWAL', 'OTHER'
    )),
    CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'AT_RISK', 'OVERDUE', 'COMPLETED', 'VERIFIED',
        'WAIVED', 'CANCELLED'
    )),
    CHECK (criticality IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'))
);

CREATE INDEX ix_contract_obligation_due
    ON contract_obligation (organization_id, project_id, status, due_date);

CREATE TABLE obligation_occurrence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    obligation_id UUID NOT NULL,
    occurrence_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    UNIQUE (obligation_id, occurrence_date),
    FOREIGN KEY (obligation_id, organization_id)
        REFERENCES contract_obligation(id, organization_id),
    CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'AT_RISK', 'OVERDUE', 'COMPLETED', 'VERIFIED',
        'WAIVED', 'CANCELLED'
    ))
);

CREATE TABLE obligation_evidence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    obligation_id UUID NOT NULL,
    document_id UUID,
    evidence_fragment_id UUID,
    submitted_by VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    verification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    verified_by VARCHAR(255),
    verified_at TIMESTAMPTZ,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (obligation_id, organization_id)
        REFERENCES contract_obligation(id, organization_id),
    FOREIGN KEY (document_id, organization_id)
        REFERENCES document(id, organization_id),
    FOREIGN KEY (evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    CHECK (verification_status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_obligation_evidence_obligation
    ON obligation_evidence (organization_id, obligation_id, submitted_at DESC);

CREATE TABLE historical_tender_evaluation_case (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    case_code VARCHAR(120) NOT NULL,
    project_id UUID,
    requirement_id UUID,
    expert_decision VARCHAR(40) NOT NULL,
    expected_compliance_decision VARCHAR(40) NOT NULL,
    expected_risk_level VARCHAR(40),
    expected_bid_decision VARCHAR(40),
    capability_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, case_code),
    CHECK (expert_decision IN (
        'COMPLIANT', 'NON_COMPLIANT', 'INSUFFICIENT_INFORMATION', 'NOT_APPLICABLE'
    )),
    CHECK (expected_compliance_decision IN (
        'COMPLIANT', 'NON_COMPLIANT', 'INSUFFICIENT_INFORMATION', 'NOT_APPLICABLE'
    ))
);
