package com.nanobase.specai.decision.application;

import com.nanobase.specai.decision.application.BidDecisionEngine.Recommendation;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenderSummaryService {
    private final JdbcTemplate jdbc;
    private final BidDecisionEngine engine;
    private final FeatureFlagService flags;
    private final Clock clock;

    @Autowired
    public TenderSummaryService(JdbcTemplate jdbc, BidDecisionEngine engine,
                                FeatureFlagService flags) {
        this(jdbc, engine, flags, Clock.systemUTC());
    }

    TenderSummaryService(JdbcTemplate jdbc, BidDecisionEngine engine, FeatureFlagService flags,
                         Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.flags = flags;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> rebuild(UUID organizationId, UUID projectId) {
        Instant now = clock.instant();
        int total = count(organizationId, projectId, """
            select count(*) from requirement
             where organization_id = ? and project_id = ?
               and requirement_status = 'ACTIVE'
            """);
        int mandatory = count(organizationId, projectId, """
            select count(*) from requirement
             where organization_id = ? and project_id = ?
               and requirement_status = 'ACTIVE' and obligation_level = 'MANDATORY'
            """);
        int compliantMandatory = count(organizationId, projectId, """
            select count(*) from compliance_evaluation evaluation
            join ontology_concept decision on decision.id = coalesce(
                evaluation.final_decision_concept_id, evaluation.suggested_decision_concept_id)
            join requirement requirement on requirement.id = evaluation.requirement_id
             where evaluation.organization_id = ? and evaluation.project_id = ?
               and requirement.obligation_level = 'MANDATORY'
               and requirement.requirement_status = 'ACTIVE'
               and decision.concept_code = 'COMPLIANT'
            """);
        int nonCompliantMandatory = count(organizationId, projectId, """
            select count(*) from compliance_evaluation evaluation
            join ontology_concept decision on decision.id = coalesce(
                evaluation.final_decision_concept_id, evaluation.suggested_decision_concept_id)
            join requirement requirement on requirement.id = evaluation.requirement_id
             where evaluation.organization_id = ? and evaluation.project_id = ?
               and requirement.obligation_level = 'MANDATORY'
               and requirement.requirement_status = 'ACTIVE'
               and decision.concept_code = 'NON_COMPLIANT'
            """);
        int unknownMandatory = Math.max(0, mandatory - compliantMandatory - nonCompliantMandatory);
        int hardBlockers = count(organizationId, projectId, """
            select count(*) from compliance_gap
             where organization_id = ? and project_id = ?
               and status in ('OPEN', 'PLANNED', 'IN_PROGRESS')
               and remediability = 'HARD_BLOCKER'
            """);
        int remediable = count(organizationId, projectId, """
            select count(*) from compliance_gap
             where organization_id = ? and project_id = ?
               and status in ('OPEN', 'PLANNED', 'IN_PROGRESS')
               and remediability = 'REMEDIABLE_BEFORE_BID'
            """);
        int clarifications = count(organizationId, projectId, """
            select count(*) from clarification_request
             where organization_id = ? and project_id = ?
            """);
        int criticalRisks = count(organizationId, projectId, """
            select count(*) from risk_record
             where organization_id = ? and project_id = ?
            """);

        var decision = engine.decide(new BidDecisionEngine.DecisionInput(
            mandatory, compliantMandatory, nonCompliantMandatory, unknownMandatory,
            hardBlockers, remediable, clarifications, criticalRisks, 0,
            mandatory > 0 && mandatory == compliantMandatory,
            true, true));

        String overallCompliance = overallCompliance(mandatory, compliantMandatory,
            nonCompliantMandatory, unknownMandatory, hardBlockers);
        String overallRisk = criticalRisks > 0 ? "CRITICAL" : hardBlockers > 0 ? "HIGH" : "MEDIUM";

        UUID existing = jdbc.query("""
            select id from tender_assessment_summary
             where organization_id = ? and project_id = ?
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            organizationId, projectId);
        if (existing == null) {
            jdbc.update("""
                insert into tender_assessment_summary (
                    id, organization_id, project_id, total_requirements, mandatory_requirements,
                    compliant_mandatory, non_compliant_mandatory, unknown_mandatory,
                    hard_blocker_count, remediable_gap_count, clarification_count,
                    critical_risk_count, high_risk_count, overall_compliance_status,
                    overall_risk_level, recommended_bid_decision, generated_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), organizationId, projectId, total, mandatory,
                compliantMandatory, nonCompliantMandatory, unknownMandatory, hardBlockers,
                remediable, clarifications, criticalRisks, overallCompliance, overallRisk,
                decision.recommendation().name(), now, now, now);
        } else {
            jdbc.update("""
                update tender_assessment_summary set
                    total_requirements = ?, mandatory_requirements = ?,
                    compliant_mandatory = ?, non_compliant_mandatory = ?, unknown_mandatory = ?,
                    hard_blocker_count = ?, remediable_gap_count = ?, clarification_count = ?,
                    critical_risk_count = ?, overall_compliance_status = ?, overall_risk_level = ?,
                    recommended_bid_decision = ?, generated_at = ?, updated_at = ?
                 where organization_id = ? and project_id = ?
                """, total, mandatory, compliantMandatory, nonCompliantMandatory, unknownMandatory,
                hardBlockers, remediable, clarifications, criticalRisks, overallCompliance,
                overallRisk, decision.recommendation().name(), now, now, organizationId, projectId);
        }
        return get(organizationId, projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID organizationId, UUID projectId) {
        var rows = jdbc.queryForList("""
            select * from tender_assessment_summary
             where organization_id = ? and project_id = ?
            """, organizationId, projectId);
        if (rows.isEmpty()) {
            return Map.of("projectId", projectId, "status", "NOT_GENERATED");
        }
        return rows.getFirst();
    }

    public boolean enabled(UUID organizationId, UUID projectId) {
        return flags.enabled(organizationId, projectId, TenderIntelligenceFlags.TENDER_DOMAIN_V2)
            || flags.enabled(organizationId, projectId, TenderIntelligenceFlags.BID_DECISION);
    }

    private int count(UUID organizationId, UUID projectId, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class, organizationId, projectId);
        return value == null ? 0 : value;
    }

    private String overallCompliance(int mandatory, int compliant, int nonCompliant,
                                     int unknown, int hardBlockers) {
        if (hardBlockers > 0 || nonCompliant > 0) {
            return "NOT_ELIGIBLE";
        }
        if (unknown > 0) {
            return "CONDITIONALLY_ELIGIBLE";
        }
        if (mandatory > 0 && mandatory == compliant) {
            return "ELIGIBLE";
        }
        return "REVIEW_REQUIRED";
    }
}
