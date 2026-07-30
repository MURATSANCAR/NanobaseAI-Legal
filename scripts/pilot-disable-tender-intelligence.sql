-- Emergency / staged disable for pilot tender-intelligence flags.
-- Usage:
--   psql "$DATABASE_URL" -v pilot_org_id="$PILOT_ORGANIZATION_ID" \
--     -v ON_ERROR_STOP=1 -f scripts/pilot-disable-tender-intelligence.sql
-- Prefer global env kill switches for instant NO-GO.

BEGIN;

UPDATE feature_assignment assignment
   SET enabled = FALSE,
       updated_at = now()
  FROM feature_definition definition
 WHERE assignment.feature_definition_id = definition.id
   AND assignment.organization_id = :'pilot_org_id'::uuid
   AND definition.feature_code IN (
       'TENDER_DOMAIN_V2_ENABLED',
       'REQUIREMENT_CLASSIFICATION_ENABLED',
       'COMPANY_CAPABILITY_REGISTRY_ENABLED',
       'DETERMINISTIC_EVALUATION_ENABLED',
       'GAP_ANALYSIS_ENABLED',
       'CLARIFICATION_MANAGEMENT_ENABLED',
       'RISK_ENGINE_ENABLED',
       'BID_DECISION_ENABLED',
       'OBLIGATION_MANAGEMENT_ENABLED'
   );

COMMIT;

SELECT definition.feature_code AS feature_key, assignment.enabled
  FROM feature_assignment assignment
  JOIN feature_definition definition
    ON definition.id = assignment.feature_definition_id
 WHERE assignment.organization_id = :'pilot_org_id'::uuid
   AND assignment.project_id IS NULL
 ORDER BY definition.feature_code;
