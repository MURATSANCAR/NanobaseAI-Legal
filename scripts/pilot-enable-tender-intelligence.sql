-- Pilot organization tender-intelligence enablement (day-1 scope).
-- Usage:
--   psql "$DATABASE_URL" -v pilot_org_id="$PILOT_ORGANIZATION_ID" \
--     -v ON_ERROR_STOP=1 -f scripts/pilot-enable-tender-intelligence.sql
--
-- Day-1 enabled: V2, classification, capability, deterministic, gap
-- Day-1 forced off: clarification, risk, bid, obligation
-- Idempotent upserts. Unknown organization fails via FK.

BEGIN;

INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code IN (
     'TENDER_DOMAIN_V2_ENABLED',
     'REQUIREMENT_CLASSIFICATION_ENABLED',
     'COMPANY_CAPABILITY_REGISTRY_ENABLED',
     'DETERMINISTIC_EVALUATION_ENABLED',
     'GAP_ANALYSIS_ENABLED'
 )
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, FALSE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code IN (
     'CLARIFICATION_MANAGEMENT_ENABLED',
     'RISK_ENGINE_ENABLED',
     'BID_DECISION_ENABLED',
     'OBLIGATION_MANAGEMENT_ENABLED'
 )
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = FALSE, updated_at = now();

COMMIT;

SELECT definition.feature_code AS feature_key, assignment.enabled
  FROM feature_assignment assignment
  JOIN feature_definition definition
    ON definition.id = assignment.feature_definition_id
 WHERE assignment.organization_id = :'pilot_org_id'::uuid
   AND assignment.project_id IS NULL
 ORDER BY definition.feature_code;
