-- Phase 3: Bid decision, assessment review, tender summary, clarification enrichment.

CREATE TABLE bid_decision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    system_recommendation VARCHAR(40) NOT NULL,
    final_decision VARCHAR(40),
    decision_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    summary TEXT NOT NULL,
    conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    rationale_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    rejected_by VARCHAR(255),
    rejected_at TIMESTAMPTZ,
    override_reason TEXT,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    UNIQUE (project_id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    CHECK (system_recommendation IN (
        'BID', 'CONDITIONAL_BID', 'NO_BID', 'MANAGEMENT_REVIEW_REQUIRED'
    )),
    CHECK (final_decision IS NULL OR final_decision IN (
        'BID', 'CONDITIONAL_BID', 'NO_BID', 'MANAGEMENT_REVIEW_REQUIRED'
    )),
    CHECK (decision_status IN (
        'DRAFT', 'REVIEW_REQUIRED', 'APPROVED', 'REJECTED', 'OVERRIDDEN'
    ))
);

CREATE TABLE assessment_review (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    assessment_id UUID NOT NULL,
    reviewer_user_id VARCHAR(255) NOT NULL,
    review_decision VARCHAR(40) NOT NULL,
    review_comment TEXT,
    override_decision VARCHAR(40),
    override_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (assessment_id, organization_id)
        REFERENCES compliance_evaluation(id, organization_id),
    CHECK (review_decision IN (
        'CONFIRMED', 'REJECTED', 'NEEDS_MORE_INFORMATION', 'OVERRIDDEN'
    ))
);

CREATE INDEX ix_assessment_review_assessment
    ON assessment_review (organization_id, assessment_id, created_at DESC);

CREATE TABLE tender_assessment_summary (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    total_requirements INTEGER NOT NULL DEFAULT 0,
    mandatory_requirements INTEGER NOT NULL DEFAULT 0,
    compliant_mandatory INTEGER NOT NULL DEFAULT 0,
    non_compliant_mandatory INTEGER NOT NULL DEFAULT 0,
    unknown_mandatory INTEGER NOT NULL DEFAULT 0,
    hard_blocker_count INTEGER NOT NULL DEFAULT 0,
    remediable_gap_count INTEGER NOT NULL DEFAULT 0,
    clarification_count INTEGER NOT NULL DEFAULT 0,
    critical_risk_count INTEGER NOT NULL DEFAULT 0,
    high_risk_count INTEGER NOT NULL DEFAULT 0,
    technical_risk_score NUMERIC(10, 4) NOT NULL DEFAULT 0,
    financial_risk_score NUMERIC(10, 4) NOT NULL DEFAULT 0,
    contractual_risk_score NUMERIC(10, 4) NOT NULL DEFAULT 0,
    operational_risk_score NUMERIC(10, 4) NOT NULL DEFAULT 0,
    overall_compliance_status VARCHAR(40) NOT NULL DEFAULT 'REVIEW_REQUIRED',
    overall_risk_level VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    recommended_bid_decision VARCHAR(40) NOT NULL DEFAULT 'MANAGEMENT_REVIEW_REQUIRED',
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    UNIQUE (project_id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    CHECK (overall_compliance_status IN (
        'ELIGIBLE', 'CONDITIONALLY_ELIGIBLE', 'NOT_ELIGIBLE', 'REVIEW_REQUIRED'
    )),
    CHECK (overall_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CHECK (recommended_bid_decision IN (
        'BID', 'CONDITIONAL_BID', 'NO_BID', 'MANAGEMENT_REVIEW_REQUIRED'
    ))
);

-- Enrich existing clarification_request with tender-intelligence fields if missing.
ALTER TABLE clarification_request
    ADD COLUMN IF NOT EXISTS requirement_id UUID,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS submission_deadline DATE,
    ADD COLUMN IF NOT EXISTS response_document_id UUID,
    ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(255);
