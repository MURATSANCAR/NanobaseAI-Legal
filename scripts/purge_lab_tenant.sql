-- Hard purge all lab tender projects + derived artifacts for bootstrap org.
BEGIN;
SET LOCAL app.current_organization_id = '11111111-1111-1111-1111-111111111111';

TRUNCATE company_fit_report, company_capability, company_document CASCADE;

CREATE TEMP TABLE _purge_projects AS SELECT id FROM tender_project;
CREATE TEMP TABLE _purge_docs AS SELECT id FROM document;
CREATE TEMP TABLE _purge_versions AS SELECT id FROM document_version;

-- project-scoped operational tables
DELETE FROM assessment_review WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM bid_decision WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM tender_assessment_summary WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM approval_request WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM clarification_candidate WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM clarification_request WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM conflict_record WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM decision_support_case WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM expert_feedback WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM impact_analysis_job WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM knowledge_snapshot WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM project_finalization_record WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM report_data_snapshot WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM report_generation_job WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM risk_analysis_job WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM risk_analysis_profile WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM risk_record WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM task_record WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM terminology_snapshot WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM workflow_instance WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM ambiguity_finding WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM compliance_analysis_job WHERE project_id IN (SELECT id FROM _purge_projects);

-- compliance / contracts
DELETE FROM compliance_condition WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM compliance_gap WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM compliance_evaluation WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM obligation_evidence WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM contract_obligation WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM contract_record WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;

-- requirement graph dependents
DELETE FROM requirement_matching_task WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM requirement_capability_match WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM requirement_condition WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM requirement_dependency
WHERE source_requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects))
   OR target_requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));

DELETE FROM ambiguity_source WHERE document_id IN (SELECT id FROM _purge_docs) OR requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM clarification_source WHERE document_id IN (SELECT id FROM _purge_docs) OR requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM conflict_source WHERE document_id IN (SELECT id FROM _purge_docs) OR requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM risk_source WHERE document_id IN (SELECT id FROM _purge_docs) OR requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM clarification_answer WHERE document_id IN (SELECT id FROM _purge_docs);
DELETE FROM evidence_scope_declaration WHERE document_id IN (SELECT id FROM _purge_docs) OR project_id IN (SELECT id FROM _purge_projects);
DELETE FROM evidence_fragment WHERE document_id IN (SELECT id FROM _purge_docs);
DELETE FROM task_attachment WHERE document_id IN (SELECT id FROM _purge_docs);

DELETE FROM document_change_item
WHERE change_set_id IN (
  SELECT id FROM document_change_set WHERE project_id IN (SELECT id FROM _purge_projects)
)
OR base_requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects))
OR target_requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM document_change_set WHERE project_id IN (SELECT id FROM _purge_projects);

UPDATE requirement SET superseded_by_requirement_id = NULL
WHERE project_id IN (SELECT id FROM _purge_projects);

DELETE FROM requirement_source_fragment
WHERE requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM requirement_revision
WHERE requirement_id IN (SELECT id FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects));
DELETE FROM requirement WHERE project_id IN (SELECT id FROM _purge_projects);

-- clause analysis leftovers
DELETE FROM model_routing_decision
WHERE source_clause_id IN (SELECT id FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions));
DELETE FROM model_run
WHERE source_clause_id IN (SELECT id FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions));
DELETE FROM evaluation_case
WHERE source_clause_id IN (SELECT id FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions));
DELETE FROM prompt_security_assessment
WHERE clause_id IN (SELECT id FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions))
   OR document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM clause_chunk
WHERE clause_id IN (SELECT id FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions));

DELETE FROM knowledge_extraction_job WHERE document_id IN (SELECT id FROM _purge_docs);
DELETE FROM knowledge_extraction_profile WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM requirement_extraction_job WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM analysis_profile WHERE project_id IN (SELECT id FROM _purge_projects);

DELETE FROM document_processing_job WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM processing_event WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM external_document_mapping WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM document_capability_profile WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM ocr_quality_assessment WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM document_table_cell
WHERE table_id IN (SELECT id FROM document_table WHERE document_version_id IN (SELECT id FROM _purge_versions));
DELETE FROM document_table WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM docx_structure_block WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM parser_warning WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM file_security_assessment WHERE document_version_id IN (SELECT id FROM _purge_versions) OR project_id IN (SELECT id FROM _purge_projects);
DELETE FROM document_layout_block WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM recurring_page_element WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM document_page WHERE document_version_id IN (SELECT id FROM _purge_versions);
DELETE FROM clause WHERE document_version_id IN (SELECT id FROM _purge_versions);

DELETE FROM capability WHERE source_document_id IN (SELECT id FROM _purge_docs);

UPDATE document
SET current_version_id = NULL,
    superseded_by_document_id = NULL,
    supersedes_document_id = NULL
WHERE id IN (SELECT id FROM _purge_docs);

DELETE FROM document_version WHERE id IN (SELECT id FROM _purge_versions);
DELETE FROM document WHERE id IN (SELECT id FROM _purge_docs);

DELETE FROM project_member WHERE project_id IN (SELECT id FROM _purge_projects);
DELETE FROM tender_project WHERE id IN (SELECT id FROM _purge_projects);

DELETE FROM audit_event WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM outbox_message WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;
DELETE FROM consumer_idempotency WHERE organization_id = '11111111-1111-1111-1111-111111111111'::uuid;

COMMIT;
