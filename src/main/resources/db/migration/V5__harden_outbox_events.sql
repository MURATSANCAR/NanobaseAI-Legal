ALTER TABLE outbox_event DROP CONSTRAINT ck_outbox_status;

UPDATE outbox_event
SET status = CASE
    WHEN status = 'PROCESSING' THEN 'PENDING'
    WHEN status = 'FAILED' AND retry_count >= 10 THEN 'DEAD'
    ELSE status
END;

ALTER TABLE outbox_event
    ADD COLUMN event_id UUID,
    ADD COLUMN claimed_by VARCHAR(255),
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE outbox_event
SET event_id = id,
    updated_at = COALESCE(published_at, created_at);

ALTER TABLE outbox_event
    ALTER COLUMN event_id SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id),
    ADD CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED', 'DEAD')),
    ADD CONSTRAINT ck_outbox_claim
        CHECK (
            (status = 'CLAIMED' AND claimed_by IS NOT NULL AND claimed_at IS NOT NULL)
            OR status <> 'CLAIMED'
        );

CREATE INDEX ix_outbox_claim_timeout
    ON outbox_event (status, claimed_at)
    WHERE status = 'CLAIMED';

