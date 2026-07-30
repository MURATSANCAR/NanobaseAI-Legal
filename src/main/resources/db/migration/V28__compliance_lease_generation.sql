-- Lease fencing tokens and prepare/execute task statuses support.
ALTER TABLE compliance_analysis_job
    ADD COLUMN IF NOT EXISTS lease_generation BIGINT NOT NULL DEFAULT 0;

ALTER TABLE requirement_matching_task
    ADD COLUMN IF NOT EXISTS lease_generation BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS ix_compliance_job_status_lease
    ON compliance_analysis_job (status, lease_expires_at);

CREATE INDEX IF NOT EXISTS ix_matching_task_job_status_attempt
    ON requirement_matching_task (compliance_job_id, status, next_attempt_at);
