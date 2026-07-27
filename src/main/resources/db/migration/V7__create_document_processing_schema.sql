ALTER TABLE document DROP CONSTRAINT ck_document_status;
ALTER TABLE document_version DROP CONSTRAINT ck_document_version_status;

ALTER TABLE document
    ADD CONSTRAINT ck_document_status CHECK (status IN (
        'UPLOADED', 'VIRUS_SCANNING', 'CLASSIFYING', 'QUEUED', 'PARSING',
        'OCR_PROCESSING', 'STRUCTURE_DETECTION', 'INDEXING', 'READY', 'FAILED',
        'MANUAL_REVIEW_REQUIRED', 'CANCELLED'
    ));
ALTER TABLE document_version
    ADD CONSTRAINT ck_document_version_status CHECK (processing_status IN (
        'UPLOADED', 'VIRUS_SCANNING', 'CLASSIFYING', 'QUEUED', 'PARSING',
        'OCR_PROCESSING', 'STRUCTURE_DETECTION', 'INDEXING', 'READY', 'FAILED',
        'MANUAL_REVIEW_REQUIRED', 'CANCELLED'
    ));

CREATE TABLE document_processing_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    provider VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    current_stage VARCHAR(50) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    external_reference VARCHAR(255),
    correlation_id UUID NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_processing_job_status CHECK (status IN (
        'UPLOADED', 'VIRUS_SCANNING', 'CLASSIFYING', 'QUEUED', 'PARSING',
        'OCR_PROCESSING', 'STRUCTURE_DETECTION', 'INDEXING', 'READY', 'FAILED',
        'MANUAL_REVIEW_REQUIRED', 'CANCELLED'
    )),
    CONSTRAINT ck_processing_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_processing_job_attempts CHECK (attempt_count >= 0)
);

CREATE TABLE processing_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    processing_job_id UUID NOT NULL REFERENCES document_processing_job(id) ON DELETE CASCADE,
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    stage VARCHAR(50) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(500) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_processing_event_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE document_page (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    width NUMERIC(12,4),
    height NUMERIC(12,4),
    rotation INTEGER NOT NULL DEFAULT 0,
    raw_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    text_quality_score NUMERIC(6,5),
    thumbnail_object_key VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_document_page_version_number UNIQUE (document_version_id, page_number),
    CONSTRAINT ck_document_page_number CHECK (page_number > 0),
    CONSTRAINT ck_document_page_rotation CHECK (rotation IN (0, 90, 180, 270)),
    CONSTRAINT ck_document_page_quality
        CHECK (text_quality_score IS NULL OR text_quality_score BETWEEN 0 AND 1)
);

ALTER TABLE clause RENAME COLUMN parent_id TO parent_clause_id;
ALTER TABLE clause RENAME COLUMN source_text TO raw_text;
ALTER TABLE clause RENAME COLUMN page_number TO page_start;
ALTER TABLE clause
    ALTER COLUMN clause_number DROP NOT NULL,
    ALTER COLUMN title DROP NOT NULL,
    ADD COLUMN normalized_text TEXT,
    ADD COLUMN clause_type VARCHAR(80),
    ADD COLUMN page_end INTEGER,
    ADD COLUMN bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE clause
SET normalized_text = raw_text,
    page_end = page_start,
    content_hash = md5(raw_text) || md5(raw_text),
    updated_at = created_at;
ALTER TABLE clause
    ALTER COLUMN normalized_text SET NOT NULL,
    ALTER COLUMN page_end SET NOT NULL,
    ALTER COLUMN content_hash SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT fk_clause_parent
        FOREIGN KEY (parent_clause_id) REFERENCES clause(id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_clause_page_range
        CHECK (page_start > 0 AND page_end >= page_start);

CREATE TABLE document_table (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    page_start INTEGER NOT NULL,
    page_end INTEGER NOT NULL,
    caption VARCHAR(500),
    markdown_content TEXT,
    structured_content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_document_table_page_range
        CHECK (page_start > 0 AND page_end >= page_start)
);

CREATE TABLE parser_warning (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    processing_job_id UUID NOT NULL REFERENCES document_processing_job(id) ON DELETE CASCADE,
    warning_code VARCHAR(100) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    page_number INTEGER,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_parser_warning_severity
        CHECK (severity IN ('INFO', 'WARNING', 'ERROR')),
    CONSTRAINT ck_parser_warning_page
        CHECK (page_number IS NULL OR page_number > 0)
);
