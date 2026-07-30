package com.nanobase.specai.operations.application;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feature flags require both:
 * <ol>
 *   <li>Environment master allow (default false) — runbook kill switch</li>
 *   <li>Database assignment/default — pilot organization scoping</li>
 * </ol>
 * Setting only the env var to true does not enable a feature globally unless
 * {@code feature_definition.default_state} or a {@code feature_assignment} is true.
 */
@Service
public class FeatureFlagService {
    private static final Map<String, String> ENV_KEYS = Map.of(
        TenderIntelligenceFlags.TENDER_DOMAIN_V2,
        "specai.tender-intelligence.tender-domain-v2-enabled",
        TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION,
        "specai.tender-intelligence.requirement-classification-enabled",
        TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY,
        "specai.tender-intelligence.company-capability-registry-enabled",
        TenderIntelligenceFlags.DETERMINISTIC_EVALUATION,
        "specai.tender-intelligence.deterministic-evaluation-enabled",
        TenderIntelligenceFlags.GAP_ANALYSIS,
        "specai.tender-intelligence.gap-analysis-enabled",
        TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT,
        "specai.tender-intelligence.clarification-management-enabled",
        TenderIntelligenceFlags.RISK_ENGINE,
        "specai.tender-intelligence.risk-engine-enabled",
        TenderIntelligenceFlags.BID_DECISION,
        "specai.tender-intelligence.bid-decision-enabled",
        TenderIntelligenceFlags.OBLIGATION_MANAGEMENT,
        "specai.tender-intelligence.obligation-management-enabled"
    );

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public FeatureFlagService(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID organizationId, UUID projectId, String featureCode) {
        if (!environmentAllows(featureCode)) {
            return false;
        }
        Boolean value = jdbc.queryForObject("""
            select coalesce(
                (
                    select assignment.enabled
                      from feature_assignment assignment
                      join feature_definition definition
                        on definition.id = assignment.feature_definition_id
                     where assignment.organization_id = ?
                       and definition.feature_code = ?
                       and (assignment.valid_from is null or assignment.valid_from <= now())
                       and (assignment.valid_until is null or assignment.valid_until > now())
                       and (assignment.project_id = ? or assignment.project_id is null)
                     order by (assignment.project_id is not null) desc,
                              assignment.updated_at desc
                     limit 1
                ),
                (
                    select default_state from feature_definition
                     where feature_code = ?
                ),
                false
            )
            """, Boolean.class, organizationId, featureCode, projectId, featureCode);
        return Boolean.TRUE.equals(value);
    }

    public boolean environmentAllows(String featureCode) {
        String property = ENV_KEYS.get(featureCode);
        if (property == null) {
            // Non-tender-intelligence flags remain DB-only.
            return true;
        }
        return environment.getProperty(property, Boolean.class, false);
    }

    public Map<String, Boolean> tenderIntelligenceEnvironmentSnapshot() {
        return Map.of(
            TenderIntelligenceFlags.TENDER_DOMAIN_V2,
            environmentAllows(TenderIntelligenceFlags.TENDER_DOMAIN_V2),
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION,
            environmentAllows(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION),
            TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY,
            environmentAllows(TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY),
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION,
            environmentAllows(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION),
            TenderIntelligenceFlags.GAP_ANALYSIS,
            environmentAllows(TenderIntelligenceFlags.GAP_ANALYSIS),
            TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT,
            environmentAllows(TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT),
            TenderIntelligenceFlags.RISK_ENGINE,
            environmentAllows(TenderIntelligenceFlags.RISK_ENGINE),
            TenderIntelligenceFlags.BID_DECISION,
            environmentAllows(TenderIntelligenceFlags.BID_DECISION),
            TenderIntelligenceFlags.OBLIGATION_MANAGEMENT,
            environmentAllows(TenderIntelligenceFlags.OBLIGATION_MANAGEMENT)
        );
    }

    public String describe(String featureCode) {
        return featureCode + "=" + environmentAllows(featureCode);
    }

    public static String normalize(String featureCode) {
        return featureCode == null ? "" : featureCode.trim().toUpperCase(Locale.ROOT);
    }
}
