-- Phase 1: Requirement classification and lifecycle fields (backward-compatible backfill).

ALTER TABLE requirement
    ADD COLUMN IF NOT EXISTS requirement_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS obligation_level VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS lifecycle_stage VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS criticality VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS evaluation_method VARCHAR(40) NOT NULL DEFAULT 'MANUAL_REVIEW',
    ADD COLUMN IF NOT EXISTS consequence_type VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS remediability VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS responsible_department VARCHAR(120),
    ADD COLUMN IF NOT EXISTS deadline_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS explicit_deadline DATE,
    ADD COLUMN IF NOT EXISTS relative_deadline_days INTEGER,
    ADD COLUMN IF NOT EXISTS scoring_weight NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS minimum_score NUMERIC(10, 4),
    ADD COLUMN IF NOT EXISTS closed_world_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_clarification BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requirement_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS superseded_by_requirement_id UUID;

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_type;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_type CHECK (requirement_type IN (
        'LEGAL', 'ADMINISTRATIVE', 'TECHNICAL', 'FINANCIAL', 'COMMERCIAL', 'OPERATIONAL',
        'SECURITY', 'PRIVACY', 'QUALITY', 'CERTIFICATION', 'PERSONNEL', 'EXPERIENCE',
        'REFERENCE', 'INFRASTRUCTURE', 'SLA', 'CONTRACTUAL', 'DELIVERABLE', 'REPORTING',
        'INSURANCE', 'GUARANTEE', 'OTHER'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_obligation_level;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_obligation_level CHECK (obligation_level IN (
        'MANDATORY', 'SCORING', 'PREFERRED', 'INFORMATIONAL', 'CONDITIONAL', 'UNKNOWN'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_lifecycle_stage;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_lifecycle_stage CHECK (lifecycle_stage IN (
        'PRE_QUALIFICATION', 'BID_SUBMISSION', 'EVALUATION', 'PRE_CONTRACT', 'POST_AWARD',
        'CONTRACT_EXECUTION', 'ONGOING', 'CONTRACT_CLOSEOUT', 'UNKNOWN'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_criticality;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_criticality CHECK (criticality IN (
        'CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_evaluation_method;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_evaluation_method CHECK (evaluation_method IN (
        'BOOLEAN', 'NUMERIC_THRESHOLD', 'DATE_VALIDITY', 'DOCUMENT_PRESENCE',
        'CERTIFICATE_VALIDITY', 'PERSONNEL_COUNT', 'EXPERIENCE_DURATION',
        'FINANCIAL_RATIO', 'TEXTUAL_COMPLIANCE', 'MULTI_CONDITION', 'MANUAL_REVIEW'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_consequence_type;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_consequence_type CHECK (consequence_type IN (
        'DISQUALIFICATION', 'SCORE_LOSS', 'BID_REJECTION', 'CONTRACT_BREACH', 'PENALTY',
        'TERMINATION', 'PAYMENT_DELAY', 'OPERATIONAL_RISK', 'REPUTATIONAL_RISK',
        'INFORMATION_ONLY', 'UNKNOWN'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_remediability;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_remediability CHECK (remediability IN (
        'HARD_BLOCKER', 'REMEDIABLE_BEFORE_BID', 'REMEDIABLE_BEFORE_CONTRACT',
        'REMEDIABLE_AFTER_AWARD', 'REMEDIABLE_DURING_CONTRACT', 'NOT_APPLICABLE', 'UNKNOWN'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_status;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_status CHECK (requirement_status IN (
        'ACTIVE', 'SUPERSEDED', 'REVOKED', 'DRAFT'
    ));

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS fk_requirement_superseded_by;
ALTER TABLE requirement
    ADD CONSTRAINT fk_requirement_superseded_by
        FOREIGN KEY (superseded_by_requirement_id, organization_id)
        REFERENCES requirement(id, organization_id);

CREATE INDEX IF NOT EXISTS ix_requirement_project_obligation
    ON requirement (organization_id, project_id, obligation_level, requirement_status);
CREATE INDEX IF NOT EXISTS ix_requirement_project_lifecycle
    ON requirement (organization_id, project_id, lifecycle_stage, requirement_status);
