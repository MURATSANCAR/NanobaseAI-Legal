package com.nanobase.specai.compliance.application;

import com.nanobase.specai.analysis.domain.Remediability;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompliancePostAssessmentHooks {
    private final FeatureFlagService flags;
    private final GapAnalysisService gaps;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public CompliancePostAssessmentHooks(FeatureFlagService flags, GapAnalysisService gaps,
                                         JdbcTemplate jdbc, AuditService audit) {
        this(flags, gaps, jdbc, audit, Clock.systemUTC());
    }

    CompliancePostAssessmentHooks(FeatureFlagService flags, GapAnalysisService gaps,
                                  JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.flags = flags;
        this.gaps = gaps;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void afterAssessment(UUID organizationId, UUID projectId, UUID requirementId,
                                UUID assessmentId, String decisionCode, String gapHint,
                                List<String> missingElements, boolean ambiguousRequirement,
                                String actor) {
        if ("FAILED".equalsIgnoreCase(decisionCode) || decisionCode == null) {
            return;
        }
        if ("COMPLIANT".equalsIgnoreCase(decisionCode)) {
            resolveOpenGaps(organizationId, projectId, requirementId, actor);
            return;
        }
        if (!flags.enabled(organizationId, projectId, TenderIntelligenceFlags.GAP_ANALYSIS)) {
            maybeClarify(organizationId, projectId, requirementId, decisionCode,
                ambiguousRequirement, actor);
            return;
        }
        if ("NON_COMPLIANT".equalsIgnoreCase(decisionCode)
            || "INSUFFICIENT_INFORMATION".equalsIgnoreCase(decisionCode)) {
            String gapType = classifyGap(decisionCode, gapHint, missingElements,
                ambiguousRequirement);
            Remediability remediability = "NON_COMPLIANT".equalsIgnoreCase(decisionCode)
                ? Remediability.UNKNOWN : Remediability.UNKNOWN;
            try {
                UUID gapId = gaps.createGapIdempotent(organizationId, projectId,
                    new GapAnalysisService.GapDraft(
                        requirementId, assessmentId, gapType,
                        "NON_COMPLIANT".equalsIgnoreCase(decisionCode) ? "HIGH" : "MEDIUM",
                        remediability,
                        gapType.replace('_', ' '),
                        "Auto-generated from compliance assessment " + decisionCode,
                        missingElements == null ? List.of() : missingElements,
                        recommendedAction(gapType), null, null, null, null),
                    actor);
                if (gapId != null) {
                    audit.recordSystem(organizationId, actor, "COMPLIANCE_GAP_CREATED",
                        "ComplianceGap", gapId,
                        null, Map.of("requirementId", requirementId, "projectId", projectId,
                            "gapType", gapType, "assessmentId", assessmentId));
                }
            } catch (DuplicateKeyException ignored) {
                // Idempotent open-gap unique index prevents duplicates on retry.
            }
        }
        maybeClarify(organizationId, projectId, requirementId, decisionCode,
            ambiguousRequirement, actor);
    }

    private void maybeClarify(UUID organizationId, UUID projectId, UUID requirementId,
                              String decisionCode, boolean ambiguousRequirement, String actor) {
        if (!flags.enabled(organizationId, projectId,
            TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT)) {
            return;
        }
        if (!ambiguousRequirement
            && !"INSUFFICIENT_INFORMATION".equalsIgnoreCase(decisionCode)) {
            return;
        }
        Boolean requiresClarification = jdbc.query("""
            select requires_clarification from requirement
             where id = ? and organization_id = ?
            """, rs -> rs.next() && rs.getBoolean(1), requirementId, organizationId);
        if (!Boolean.TRUE.equals(requiresClarification) && !ambiguousRequirement) {
            return;
        }
        String question = "Kurumdan netleştirmesini istediğimiz madde için minimum sayısal "
            + "eşik, kabul kriteri veya zorunluluk kapsamını açıklayabilir misiniz?";
        String normalized = normalize(question);
        Integer existing = jdbc.queryForObject("""
            select count(*) from clarification_request
             where organization_id = ? and project_id = ?
               and requirement_id = ? and normalized_question = ?
            """, Integer.class, organizationId, projectId, requirementId, normalized);
        if (existing != null && existing > 0) {
            return;
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        // Minimal draft row using existing clarification_request columns where possible.
        try {
            jdbc.update("""
                insert into clarification_request (
                    id, organization_id, project_id, source_type, source_id, question_code,
                    question_text, reason, priority_concept_id, status_concept_id,
                    created_at, updated_at, requirement_id, priority, normalized_question
                )
                select ?, ?, ?, 'REQUIREMENT', ?, ?, ?, ?,
                       (select id from ontology_concept
                         where concept_type = 'PRIORITY' and active = true
                         order by sort_order limit 1),
                       (select id from ontology_concept
                         where concept_type = 'CLARIFICATION_STATUS' and active = true
                         order by sort_order limit 1),
                       ?, ?, ?, 'MEDIUM', ?
                """, id, organizationId, projectId, requirementId,
                "REQ-" + requirementId.toString().substring(0, 8), question,
                "Ambiguous or insufficient requirement interpretation", now, now,
                requirementId, normalized);
            audit.recordSystem(organizationId, actor, "CLARIFICATION_CREATED",
                "ClarificationRequest", id, null,
                Map.of("projectId", projectId, "requirementId", requirementId));
        } catch (Exception ignored) {
            // Clarification schema may not have compatible ontology concepts in all envs.
        }
    }

    private void resolveOpenGaps(UUID organizationId, UUID projectId, UUID requirementId,
                                 String actor) {
        if (!flags.enabled(organizationId, projectId, TenderIntelligenceFlags.GAP_ANALYSIS)) {
            return;
        }
        List<UUID> openIds = jdbc.query("""
            select id from compliance_gap
             where organization_id = ? and project_id = ? and requirement_id = ?
               and status in ('OPEN', 'PLANNED', 'IN_PROGRESS')
            """, (rs, row) -> rs.getObject(1, UUID.class),
            organizationId, projectId, requirementId);
        Instant now = clock.instant();
        for (UUID gapId : openIds) {
            jdbc.update("""
                update compliance_gap
                   set status = 'RESOLVED', updated_at = ?
                 where id = ? and organization_id = ?
                """, now, gapId, organizationId);
            audit.recordSystem(organizationId, actor, "COMPLIANCE_GAP_UPDATED",
                "ComplianceGap", gapId,
                Map.of("status", "OPEN"), Map.of("status", "RESOLVED"));
        }
    }

    private String classifyGap(String decisionCode, String gapHint, List<String> missing,
                               boolean ambiguous) {
        if (ambiguous) {
            return "AMBIGUOUS_REQUIREMENT";
        }
        if (gapHint != null && !gapHint.isBlank()) {
            return gapHint;
        }
        if (missing != null) {
            for (String item : missing) {
                String value = item.toUpperCase(Locale.ROOT);
                if (value.contains("NUMERIC")) {
                    return "NUMERIC_SHORTFALL";
                }
                if (value.contains("EXPIRED")) {
                    return "EXPIRED_DOCUMENT";
                }
                if (value.contains("SCOPE")) {
                    return "SCOPE_MISMATCH";
                }
                if (value.contains("CAPABILITY") || value.contains("CERTIFICATE")) {
                    return "MISSING_CAPABILITY";
                }
            }
        }
        if ("INSUFFICIENT_INFORMATION".equalsIgnoreCase(decisionCode)) {
            return "INSUFFICIENT_EVIDENCE";
        }
        return "MISSING_CAPABILITY";
    }

    private String recommendedAction(String gapType) {
        return switch (gapType) {
            case "NUMERIC_SHORTFALL" -> "Kapasite veya mesafe kanıtını güncelleyin";
            case "MISSING_CAPABILITY" -> "Eksik yetkinliği edinin veya ortaklık kurun";
            case "EXPIRED_DOCUMENT" -> "Belgeyi yenileyin";
            case "INSUFFICIENT_EVIDENCE" -> "Kanıt dokümanı yükleyin";
            case "AMBIGUOUS_REQUIREMENT" -> "Kuruma açıklama sorun";
            default -> "Gap'i uzman incelemesine alın";
        };
    }

    private String normalize(String question) {
        return question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
