CREATE OR REPLACE FUNCTION app_current_organization_id()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_organization_id', true), '')::uuid
$$;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'tender_project',
        'document',
        'document_version',
        'external_document_mapping',
        'audit_event',
        'document_processing_job',
        'processing_event',
        'document_page',
        'clause',
        'document_table',
        'parser_warning'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_isolation_%I ON %I USING (organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name,
            table_name
        );
    END LOOP;
END
$$;

COMMENT ON FUNCTION app_current_organization_id() IS
    'Returns the transaction-local verified tenant context used by RLS policies.';

