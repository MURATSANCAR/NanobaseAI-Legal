-- Production integration: classification status + gap idempotency + clarification dedupe.
-- Additive only; no destructive changes. Feature flags remain default OFF.

ALTER TABLE requirement
    ADD COLUMN IF NOT EXISTS classification_status VARCHAR(40) NOT NULL DEFAULT 'SKIPPED';

ALTER TABLE requirement DROP CONSTRAINT IF EXISTS ck_requirement_classification_status;
ALTER TABLE requirement
    ADD CONSTRAINT ck_requirement_classification_status CHECK (classification_status IN (
        'SKIPPED', 'SUCCEEDED', 'FAILED', 'REVIEW_REQUIRED'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_gap_open_idempotent
    ON compliance_gap (organization_id, project_id, requirement_id, gap_type)
    WHERE status IN ('OPEN', 'PLANNED', 'IN_PROGRESS');

ALTER TABLE clarification_request
    ADD COLUMN IF NOT EXISTS normalized_question VARCHAR(500);

CREATE UNIQUE INDEX IF NOT EXISTS uq_clarification_request_normalized
    ON clarification_request (organization_id, project_id, requirement_id, normalized_question)
    WHERE requirement_id IS NOT NULL AND normalized_question IS NOT NULL;

-- Soft-drop noop: there is no historical CHECK blocking PARTIALLY_COMPLETED on jobs.
ALTER TABLE compliance_analysis_job
    DROP CONSTRAINT IF EXISTS ck_compliance_job_status;
