package com.nanobase.specai.operations.application;

import com.nanobase.specai.shared.observability.PlatformMetrics;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * DB or lookup failures fail closed (disabled). Setting only the env var to true
 * does not enable a feature unless DB assignment/default is also true.
 */
@Service
public class FeatureFlagService {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

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
    private final ObjectProvider<PlatformMetrics> metrics;

    public FeatureFlagService(JdbcTemplate jdbc, Environment environment,
                              ObjectProvider<PlatformMetrics> metrics) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID organizationId, UUID projectId, String featureCode) {
        if (!environmentAllows(featureCode)) {
            return false;
        }
        try {
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
        } catch (RuntimeException ex) {
            log.warn("feature_flag_lookup_failed featureCode={} failClosed=true reason={}",
                featureCode, ex.getClass().getSimpleName());
            deny(featureCode, "db_error");
            return false;
        }
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
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        snapshot.put(TenderIntelligenceFlags.TENDER_DOMAIN_V2,
            environmentAllows(TenderIntelligenceFlags.TENDER_DOMAIN_V2));
        snapshot.put(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION,
            environmentAllows(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION));
        snapshot.put(TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY,
            environmentAllows(TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY));
        snapshot.put(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION,
            environmentAllows(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION));
        snapshot.put(TenderIntelligenceFlags.GAP_ANALYSIS,
            environmentAllows(TenderIntelligenceFlags.GAP_ANALYSIS));
        snapshot.put(TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT,
            environmentAllows(TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT));
        snapshot.put(TenderIntelligenceFlags.RISK_ENGINE,
            environmentAllows(TenderIntelligenceFlags.RISK_ENGINE));
        snapshot.put(TenderIntelligenceFlags.BID_DECISION,
            environmentAllows(TenderIntelligenceFlags.BID_DECISION));
        snapshot.put(TenderIntelligenceFlags.OBLIGATION_MANAGEMENT,
            environmentAllows(TenderIntelligenceFlags.OBLIGATION_MANAGEMENT));
        return snapshot;
    }

    public String describe(String featureCode) {
        return featureCode + "=" + environmentAllows(featureCode);
    }

    public static String normalize(String featureCode) {
        return featureCode == null ? "" : featureCode.trim().toUpperCase(Locale.ROOT);
    }

    private void deny(String featureCode, String reason) {
        PlatformMetrics platformMetrics = metrics.getIfAvailable();
        if (platformMetrics != null) {
            platformMetrics.featureGateDenied(featureCode, reason);
        }
    }
}
