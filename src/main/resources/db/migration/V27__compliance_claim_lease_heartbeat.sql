-- Compliance job/task claim, lease, heartbeat and cooperative cancel columns.
-- Supports short transaction boundaries around orchestration (no long FOR UPDATE
-- across LLM calls).

ALTER TABLE compliance_analysis_job
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_requested_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS last_error_message VARCHAR(1000);

ALTER TABLE requirement_matching_task
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS slot_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS slot_wait_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS slot_acquired_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS last_error_message VARCHAR(1000);

CREATE INDEX IF NOT EXISTS ix_compliance_job_lease
    ON compliance_analysis_job (status, lease_expires_at);

CREATE INDEX IF NOT EXISTS ix_compliance_job_cancel_requested
    ON compliance_analysis_job (cancel_requested_at)
    WHERE cancel_requested_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_matching_task_lease
    ON requirement_matching_task (compliance_job_id, status, lease_expires_at);
