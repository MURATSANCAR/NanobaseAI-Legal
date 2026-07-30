package com.nanobase.specai.decision.application;

import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class BidDecisionService {
    private final JdbcTemplate jdbc;
    private final BidDecisionEngine engine;
    private final FeatureFlagService flags;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public BidDecisionService(JdbcTemplate jdbc, BidDecisionEngine engine,
                              FeatureFlagService flags, ObjectMapper mapper) {
        this(jdbc, engine, flags, mapper, Clock.systemUTC());
    }

    BidDecisionService(JdbcTemplate jdbc, BidDecisionEngine engine, FeatureFlagService flags,
                       ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.flags = flags;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> recommendAndPersist(UUID organizationId, UUID projectId,
                                                   BidDecisionEngine.DecisionInput input,
                                                   String actor) {
        requireEnabled(organizationId, projectId);
        BidDecisionEngine.DecisionResult result = engine.decide(input);
        Instant now = clock.instant();
        UUID existing = jdbc.query("""
            select id from bid_decision
             where organization_id = ? and project_id = ?
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            organizationId, projectId);
        String conditionsJson = writeJson(result.conditions());
        String rationaleJson = writeJson(Map.of(
            "reasons", result.reasons(),
            "input", input));
        if (existing == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                insert into bid_decision (
                    id, organization_id, project_id, system_recommendation, decision_status,
                    summary, conditions_json, rationale_json, created_by, created_at, updated_at
                ) values (?, ?, ?, ?, 'REVIEW_REQUIRED', ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """, id, organizationId, projectId, result.recommendation().name(),
                String.join("; ", result.reasons()), conditionsJson, rationaleJson,
                actor, now, now);
            return get(organizationId, projectId);
        }
        jdbc.update("""
            update bid_decision
               set system_recommendation = ?,
                   decision_status = 'REVIEW_REQUIRED',
                   summary = ?,
                   conditions_json = ?::jsonb,
                   rationale_json = ?::jsonb,
                   updated_at = ?,
                   version = version + 1
             where organization_id = ? and project_id = ?
            """, result.recommendation().name(), String.join("; ", result.reasons()),
            conditionsJson, rationaleJson, now, organizationId, projectId);
        return get(organizationId, projectId);
    }

    @Transactional
    public Map<String, Object> approve(UUID organizationId, UUID projectId, String finalDecision,
                                       String overrideReason, String actor) {
        requireEnabled(organizationId, projectId);
        if (finalDecision == null || finalDecision.isBlank()) {
            throw new IllegalArgumentException("finalDecision is required");
        }
        Map<String, Object> current = get(organizationId, projectId);
        String system = String.valueOf(current.get("system_recommendation"));
        boolean override = !system.equals(finalDecision);
        if (override && (overrideReason == null || overrideReason.isBlank())) {
            throw new IllegalArgumentException("overrideReason is required when changing recommendation");
        }
        Instant now = clock.instant();
        jdbc.update("""
            update bid_decision
               set final_decision = ?,
                   decision_status = ?,
                   approved_by = ?,
                   approved_at = ?,
                   override_reason = ?,
                   updated_at = ?,
                   version = version + 1
             where organization_id = ? and project_id = ?
            """, finalDecision, override ? "OVERRIDDEN" : "APPROVED", actor, now,
            overrideReason, now, organizationId, projectId);
        return get(organizationId, projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID organizationId, UUID projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select * from bid_decision
             where organization_id = ? and project_id = ?
            """, organizationId, projectId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Bid decision not found");
        }
        return rows.getFirst();
    }

    private void requireEnabled(UUID organizationId, UUID projectId) {
        if (!flags.enabled(organizationId, projectId, TenderIntelligenceFlags.BID_DECISION)) {
            throw new IllegalStateException("BID_DECISION_ENABLED is off");
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize bid decision payload", ex);
        }
    }
}
