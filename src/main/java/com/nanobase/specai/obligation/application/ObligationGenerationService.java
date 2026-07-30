package com.nanobase.specai.obligation.application;

import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObligationGenerationService {
    private final JdbcTemplate jdbc;
    private final FeatureFlagService flags;
    private final Clock clock;

    @Autowired
    public ObligationGenerationService(JdbcTemplate jdbc, FeatureFlagService flags) {
        this(jdbc, flags, Clock.systemUTC());
    }

    ObligationGenerationService(JdbcTemplate jdbc, FeatureFlagService flags, Clock clock) {
        this.jdbc = jdbc;
        this.flags = flags;
        this.clock = clock;
    }

    @Transactional
    public UUID generateFromAward(UUID organizationId, UUID projectId, String actor) {
        if (!flags.enabled(organizationId, projectId,
            TenderIntelligenceFlags.OBLIGATION_MANAGEMENT)) {
            throw new com.nanobase.specai.operations.application.FeatureDisabledException(
                TenderIntelligenceFlags.OBLIGATION_MANAGEMENT);
        }
        Instant now = clock.instant();
        UUID contractId = UUID.randomUUID();
        jdbc.update("""
            insert into contract_record (
                id, organization_id, project_id, title, status, created_by, created_at, updated_at
            ) values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
            """, contractId, organizationId, projectId,
            "Contract for tender " + projectId, actor, now, now);

        List<Map<String, Object>> requirements = jdbc.queryForList("""
            select id, title, requirement_text, lifecycle_stage, criticality, responsible_department,
                   explicit_deadline, document_id
              from requirement
             where organization_id = ?
               and project_id = ?
               and requirement_status = 'ACTIVE'
               and lifecycle_stage in ('POST_AWARD', 'CONTRACT_EXECUTION', 'ONGOING',
                                       'PRE_CONTRACT', 'UNKNOWN')
            """, organizationId, projectId);

        for (Map<String, Object> requirement : requirements) {
            UUID obligationId = UUID.randomUUID();
            String type = mapType(String.valueOf(requirement.get("lifecycle_stage")));
            jdbc.update("""
                insert into contract_obligation (
                    id, organization_id, project_id, contract_id, source_requirement_id,
                    source_document_id, obligation_type, title, description,
                    responsible_department, due_date, status, criticality, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_STARTED', ?, ?, ?)
                """, obligationId, organizationId, projectId, contractId,
                requirement.get("id"), requirement.get("document_id"), type,
                requirement.get("title") == null ? "Obligation" : requirement.get("title"),
                requirement.get("requirement_text"), requirement.get("responsible_department"),
                requirement.get("explicit_deadline"),
                requirement.get("criticality") == null ? "MEDIUM" : requirement.get("criticality"),
                now, now);
        }

        jdbc.update("""
            update tender_project
               set status = 'CONTRACT_ACTIVE', updated_at = ?, updated_by = ?
             where id = ? and organization_id = ?
            """, now, actor, projectId, organizationId);
        return contractId;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listObligations(UUID organizationId, UUID contractId) {
        return jdbc.queryForList("""
            select * from contract_obligation
             where organization_id = ? and contract_id = ?
             order by due_date nulls last, created_at
            """, organizationId, contractId);
    }

    @Transactional
    public UUID submitEvidence(UUID organizationId, UUID obligationId, UUID documentId,
                               UUID fragmentId, String actor, String comment) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
            insert into obligation_evidence (
                id, organization_id, obligation_id, document_id, evidence_fragment_id,
                submitted_by, submitted_at, verification_status, comment, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
            """, id, organizationId, obligationId, documentId, fragmentId, actor, now, comment, now);
        jdbc.update("""
            update contract_obligation
               set status = 'IN_PROGRESS', updated_at = ?
             where id = ? and organization_id = ?
            """, now, obligationId, organizationId);
        return id;
    }

    private String mapType(String lifecycleStage) {
        return switch (lifecycleStage) {
            case "ONGOING" -> "REPORTING";
            case "CONTRACT_EXECUTION" -> "DELIVERABLE";
            case "POST_AWARD" -> "MILESTONE";
            default -> "OTHER";
        };
    }
}
