CREATE TABLE processed_message (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(150) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    result_status VARCHAR(30) NOT NULL,
    CONSTRAINT uq_processed_message_consumer_event UNIQUE (consumer_name, event_id),
    CONSTRAINT ck_processed_message_status
        CHECK (result_status IN ('PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX ix_processed_message_consumer_event
    ON processed_message (consumer_name, event_id);

