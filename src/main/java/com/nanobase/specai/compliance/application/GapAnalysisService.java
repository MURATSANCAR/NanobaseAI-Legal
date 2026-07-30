package com.nanobase.specai.compliance.application;

import com.nanobase.specai.analysis.domain.Remediability;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GapAnalysisService {
    private final JdbcTemplate jdbc;
    private final FeatureFlagService flags;
    private final Clock clock;
    private final PlatformMetrics metrics;

    @Autowired
    public GapAnalysisService(JdbcTemplate jdbc, FeatureFlagService flags,
                              ObjectProvider<PlatformMetrics> metrics) {
        this(jdbc, flags, Clock.systemUTC(), metrics.getIfAvailable());
    }

    GapAnalysisService(JdbcTemplate jdbc, FeatureFlagService flags, Clock clock) {
        this(jdbc, flags, clock, null);
    }

    GapAnalysisService(JdbcTemplate jdbc, FeatureFlagService flags, Clock clock,
                       PlatformMetrics metrics) {
        this.jdbc = jdbc;
        this.flags = flags;
        this.clock = clock;
        this.metrics = metrics;
    }

    public record GapDraft(
        UUID requirementId,
        UUID assessmentId,
        String gapType,
        String severity,
        Remediability remediability,
        String title,
        String description,
        List<String> missingElements,
        String recommendedAction,
        Integer estimatedResolutionDays,
        BigDecimal estimatedCost,
        String currency,
        String ownerDepartment
    ) {
    }

    @Transactional
    public UUID createGap(UUID organizationId, UUID projectId, GapDraft draft, String actor) {
        if (!flags.enabled(organizationId, projectId, TenderIntelligenceFlags.GAP_ANALYSIS)) {
            throw new IllegalStateException("GAP_ANALYSIS_ENABLED is off");
        }
        return createGapIdempotent(organizationId, projectId, draft, actor);
    }

    /**
     * Creates a gap unless an open gap of the same type already exists for the requirement.
     * Returns null when an open duplicate already exists.
     */
    @Transactional
    public UUID createGapIdempotent(UUID organizationId, UUID projectId, GapDraft draft,
                                    String actor) {
        if (!flags.enabled(organizationId, projectId, TenderIntelligenceFlags.GAP_ANALYSIS)) {
            return null;
        }
        UUID existing = jdbc.query("""
            select id from compliance_gap
             where organization_id = ? and project_id = ? and requirement_id = ?
               and gap_type = ?
               and status in ('OPEN', 'PLANNED', 'IN_PROGRESS')
             limit 1
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            organizationId, projectId, draft.requirementId(), draft.gapType());
        if (existing != null) {
            jdbc.update("""
                update compliance_gap
                   set assessment_id = coalesce(?, assessment_id),
                       description = ?,
                       missing_elements_json = ?::jsonb,
                       updated_at = ?
                 where id = ? and organization_id = ?
                """, draft.assessmentId(), draft.description(),
                toJsonArray(draft.missingElements()), clock.instant(), existing, organizationId);
            PlatformMetrics platformMetrics = metrics;
            if (platformMetrics != null) {
                platformMetrics.duplicateGapRejected();
            }
            return null;
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
            insert into compliance_gap (
                id, organization_id, project_id, requirement_id, assessment_id,
                gap_type, severity, remediability, title, description,
                missing_elements_json, recommended_action, estimated_resolution_days,
                estimated_cost, currency, owner_department, status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
            """, id, organizationId, projectId, draft.requirementId(), draft.assessmentId(),
            draft.gapType(), draft.severity(), draft.remediability().name(), draft.title(),
            draft.description(), toJsonArray(draft.missingElements()), draft.recommendedAction(),
            draft.estimatedResolutionDays(), draft.estimatedCost(), draft.currency(),
            draft.ownerDepartment(), now, now);
        if (metrics != null) {
            metrics.complianceGapCreated();
        }
        return id;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGaps(UUID organizationId, UUID projectId) {
        return jdbc.queryForList("""
            select * from compliance_gap
             where organization_id = ? and project_id = ?
             order by
               case severity
                 when 'CRITICAL' then 1 when 'HIGH' then 2 when 'MEDIUM' then 3 else 4 end,
               created_at desc
            """, organizationId, projectId);
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"')
                .append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                .append('"');
        }
        return builder.append(']').toString();
    }
}
