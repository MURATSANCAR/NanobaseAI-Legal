-- Hard purge lab tenant projects using replication role to bypass FK order.
-- Bootstrap org only. Safe for EasyMeeting lab reset before real E2E.
BEGIN;
SET LOCAL app.current_organization_id = '11111111-1111-1111-1111-111111111111';
SET LOCAL session_replication_role = replica;

TRUNCATE company_fit_report, company_capability, company_document CASCADE;

CREATE TEMP TABLE _purge_projects AS SELECT id FROM tender_project;
CREATE TEMP TABLE _purge_docs AS SELECT id FROM document;
CREATE TEMP TABLE _purge_versions AS SELECT id FROM document_version;

-- Null circular pointers first
UPDATE document
SET current_version_id = NULL,
    superseded_by_document_id = NULL,
    supersedes_document_id = NULL
WHERE id IN (SELECT id FROM _purge_docs);

UPDATE requirement SET superseded_by_requirement_id = NULL
WHERE project_id IN (SELECT id FROM _purge_projects);

-- Delete every table that references project/document/version/requirement for this lab set.
-- Broad org deletes for generated operational data.
DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'assessment_review',
    'bid_decision',
    'tender_assessment_summary',
    'approval_request',
    'clarification_candidate',
    'clarification_request',
    'conflict_record',
    'decision_support_case',
    'expert_feedback',
    'impact_analysis_job',
    'project_finalization_record',
    'report_artifact',
    'report_generation_job',
    'report_data_snapshot',
    'risk_propagation_candidate',
    'risk_source',
    'risk_record',
    'risk_analysis_job',
    'risk_analysis_profile',
    'task_attachment',
    'task_record',
    'terminology_snapshot',
    'workflow_instance',
    'ambiguity_finding',
    'ambiguity_source',
    'clarification_source',
    'clarification_answer',
    'conflict_source',
    'compliance_condition',
    'compliance_gap',
    'compliance_evidence_link',
    'compliance_evaluation_revision',
    'compliance_evaluation',
    'compliance_analysis_job',
    'obligation_evidence',
    'contract_obligation',
    'contract_record',
    'knowledge_snapshot',
    'knowledge_extraction_job',
    'knowledge_extraction_profile',
    'evidence_scope_declaration',
    'evidence_fragment',
    'requirement_matching_task',
    'requirement_capability_match',
    'requirement_condition',
    'requirement_dependency',
    'requirement_source_fragment',
    'requirement_revision',
    'requirement',
    'model_routing_decision',
    'model_run',
    'evaluation_case',
    'prompt_security_assessment',
    'clause_chunk',
    'requirement_extraction_event',
    'requirement_extraction_job',
    'analysis_profile',
    'document_processing_job',
    'processing_event',
    'external_document_mapping',
    'document_capability_profile',
    'ocr_quality_assessment',
    'document_change_item',
    'document_change_set',
    'document_table_cell',
    'document_table',
    'docx_structure_block',
    'parser_warning',
    'file_security_assessment',
    'document_layout_block',
    'recurring_page_element',
    'document_page',
    'clause',
    'capability',
    'document_version',
    'document',
    'project_member',
    'feature_assignment',
    'quota_assignment',
    'canary_assignment',
    'shadow_execution',
    'pilot_session',
    'feedback_case',
    'training_need',
    'tender_project'
  ]
  LOOP
    IF to_regclass(t) IS NULL THEN
      CONTINUE;
    END IF;
    BEGIN
      EXECUTE format('DELETE FROM %I', t);
    EXCEPTION WHEN undefined_table THEN
      NULL;
    END;
  END LOOP;
END $$;

DELETE FROM audit_event WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM outbox_event WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM document_access_url_audit WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;

SET LOCAL session_replication_role = origin;
COMMIT;
