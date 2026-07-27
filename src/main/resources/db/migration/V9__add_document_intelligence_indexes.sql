CREATE INDEX ix_processing_job_version_created
    ON document_processing_job (document_version_id, created_at DESC);
CREATE INDEX ix_processing_job_org_status
    ON document_processing_job (organization_id, status);
CREATE INDEX ix_processing_event_job_occurred
    ON processing_event (processing_job_id, occurred_at);
CREATE INDEX ix_processing_event_version_occurred
    ON processing_event (document_version_id, occurred_at);
CREATE INDEX ix_document_page_version_page
    ON document_page (document_version_id, page_number);
CREATE INDEX ix_clause_version_sort
    ON clause (document_version_id, sort_order);
CREATE INDEX ix_clause_version_parent
    ON clause (document_version_id, parent_clause_id);
CREATE INDEX ix_clause_version_number
    ON clause (document_version_id, clause_number);
CREATE INDEX ix_document_table_version_page
    ON document_table (document_version_id, page_start);
CREATE INDEX ix_parser_warning_job
    ON parser_warning (processing_job_id, created_at);
