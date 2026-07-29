-- FAST profile (Qwen3.5-9B) + compliance shadow / escalation persistence.
-- Profile codes remain free-form catalog values; routing mode is application config.

INSERT INTO model_deployment (
    id, model_definition_id, deployment_name, base_url, hardware_profile,
    runtime_configuration_json, health_status, active, created_at, updated_at
) VALUES (
    '40000000-0000-0000-0000-000000000053',
    '40000000-0000-0000-0000-000000000050',
    'nanobase-compliance-fast',
    'http://ai-orchestrator:8090',
    'LOCAL',
    '{
      "logicalModel":"nanobase-spec-ai",
      "runtimeModelHint":"Qwen3.5-9B",
      "reasoning":false,
      "temperature":0,
      "topP":0.8,
      "maxTokens":512,
      "contextSize":16384,
      "maxConcurrency":2,
      "queueWaitTimeoutSeconds":60,
      "responseTimeoutSeconds":300
    }'::jsonb,
    'UNKNOWN', TRUE, now(), now()
)
ON CONFLICT (model_definition_id, deployment_name) DO NOTHING;

INSERT INTO model_profile (
    id, organization_id, profile_code, name, selection_policy_json,
    fallback_profile_id, active, created_at, updated_at
) VALUES (
    '40000000-0000-0000-0000-000000000054', NULL, 'FAST', 'Fast compliance',
    '{
      "capability":"COMPLIANCE_EVALUATION",
      "qualityWeight":0.35,
      "latencyWeight":0.65,
      "deploymentIds":["40000000-0000-0000-0000-000000000053"]
    }'::jsonb,
    '40000000-0000-0000-0000-000000000052',
    TRUE, now(), now()
)
ON CONFLICT DO NOTHING;

UPDATE model_profile
SET selection_policy_json = selection_policy_json
    || '{"capability":"COMPLIANCE_EVALUATION"}'::jsonb,
    updated_at = now()
WHERE id = '40000000-0000-0000-0000-000000000052';

ALTER TABLE compliance_evaluation
    ADD COLUMN IF NOT EXISTS live_model_profile VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shadow_model_profile VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shadow_result_json JSONB,
    ADD COLUMN IF NOT EXISTS shadow_comparison_json JSONB,
    ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(120);

CREATE INDEX IF NOT EXISTS ix_compliance_evaluation_shadow_agreement
    ON compliance_evaluation (
        organization_id,
        ((shadow_comparison_json ->> 'agreement')::boolean)
    )
    WHERE shadow_comparison_json IS NOT NULL;
