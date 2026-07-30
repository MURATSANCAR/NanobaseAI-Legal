package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.EvaluationMethod;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import com.nanobase.specai.analysis.domain.RequirementConditionRepository;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Feature-flagged deterministic compliance path using requirement conditions and capabilities.
 * Returns null when deterministic evaluation is not decisive so the existing pipeline continues.
 */
@Service
public class DeterministicComplianceEvaluator {
    private final FeatureFlagService flags;
    private final RequirementConditionRepository conditions;
    private final RequirementCapabilityMatcher matcher;
    private final NumericRequirementEvaluator numericEvaluator;
    private final ClosedWorldValidator closedWorldValidator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DeterministicComplianceEvaluator(
        FeatureFlagService flags,
        RequirementConditionRepository conditions,
        RequirementCapabilityMatcher matcher,
        NumericRequirementEvaluator numericEvaluator,
        ClosedWorldValidator closedWorldValidator,
        JdbcTemplate jdbc,
        ObjectMapper mapper
    ) {
        this.flags = flags;
        this.conditions = conditions;
        this.matcher = matcher;
        this.numericEvaluator = numericEvaluator;
        this.closedWorldValidator = closedWorldValidator;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record DeterministicDecision(
        String decisionCode,
        boolean contradiction,
        boolean requiresReview,
        ObjectNode summary,
        List<UUID> matchedCapabilityIds,
        List<String> missingElements
    ) {
    }

    public DeterministicDecision evaluate(UUID organizationId, UUID projectId,
                                          UUID requirementId, String requirementText,
                                          String evaluationMethod,
                                          List<Map<String, Object>> evidenceRows) {
        if (!flags.enabled(organizationId, projectId,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION)) {
            return null;
        }
        List<RequirementCondition> requirementConditions =
            conditions.findByOrganizationIdAndRequirementIdOrderBySequenceNoAsc(
                organizationId, requirementId);
        if (requirementConditions.isEmpty()
            && !isDeterministicMethod(evaluationMethod)
            && !looksNumeric(requirementText)) {
            return null;
        }

        List<RequirementCapabilityMatcher.CapabilitySnapshot> capabilities =
            loadCapabilities(organizationId);
        if (!requirementConditions.isEmpty()) {
            var match = matcher.match(organizationId, projectId, requirementConditions,
                capabilities, LocalDate.now());
            ObjectNode summary = mapper.createObjectNode();
            summary.put("strategyProvider", "requirement-capability-matcher");
            summary.put("evaluationSource", "DETERMINISTIC");
            summary.put("matchStatus", match.status().name());
            summary.put("confidence", match.confidence());
            summary.set("matchedConditions", mapper.valueToTree(match.matchedConditionIds()));
            summary.set("missingConditions", mapper.valueToTree(match.missingConditionIds()));
            summary.set("contradictingConditions",
                mapper.valueToTree(match.contradictingConditionIds()));

            return switch (match.status()) {
                case MATCHED -> new DeterministicDecision("COMPLIANT", false, false, summary,
                    List.of(), List.of());
                case NOT_MATCHED, EXPIRED, OUT_OF_SCOPE -> new DeterministicDecision(
                    "NON_COMPLIANT", true, true, summary, List.of(),
                    match.missingConditionIds().stream().map(UUID::toString).toList());
                case PARTIALLY_MATCHED, UNKNOWN -> {
                    boolean closedWorld = closedWorldValidator.hasActiveDeclaration(
                        organizationId, projectId, null);
                    if (closedWorld && !match.missingConditionIds().isEmpty()) {
                        yield new DeterministicDecision("NON_COMPLIANT", true, true, summary,
                            List.of(),
                            match.missingConditionIds().stream().map(UUID::toString).toList());
                    }
                    yield new DeterministicDecision("INSUFFICIENT_INFORMATION", false, true,
                        summary, List.of(),
                        match.missingConditionIds().stream().map(UUID::toString).toList());
                }
            };
        }

        // Fallback: numeric threshold from evidence rows for distance-like requirements.
        RequirementCondition synthetic = syntheticNumeric(requirementText);
        if (synthetic == null) {
            return null;
        }
        BigDecimal evidenceValue = firstNumeric(evidenceRows);
        String evidenceUnit = firstUnit(evidenceRows);
        var numeric = numericEvaluator.evaluate(synthetic, evidenceValue, evidenceUnit);
        ObjectNode summary = mapper.createObjectNode();
        summary.put("strategyProvider", "numeric-requirement-evaluator");
        summary.put("evaluationSource", "DETERMINISTIC");
        summary.put("reasonCode", numeric.reasonCode());
        return switch (numeric.decision()) {
            case COMPLIANT -> new DeterministicDecision("COMPLIANT", false, false, summary,
                List.of(), List.of());
            case NON_COMPLIANT -> new DeterministicDecision("NON_COMPLIANT", true, true, summary,
                List.of(), List.of("NUMERIC_SHORTFALL"));
            case INSUFFICIENT_INFORMATION -> new DeterministicDecision(
                "INSUFFICIENT_INFORMATION", false, true, summary, List.of(),
                List.of("MISSING_EVIDENCE_VALUE"));
        };
    }

    private boolean isDeterministicMethod(String evaluationMethod) {
        if (evaluationMethod == null) {
            return false;
        }
        try {
            EvaluationMethod method = EvaluationMethod.valueOf(evaluationMethod);
            return method == EvaluationMethod.NUMERIC_THRESHOLD
                || method == EvaluationMethod.DATE_VALIDITY
                || method == EvaluationMethod.CERTIFICATE_VALIDITY
                || method == EvaluationMethod.DOCUMENT_PRESENCE
                || method == EvaluationMethod.PERSONNEL_COUNT
                || method == EvaluationMethod.EXPERIENCE_DURATION
                || method == EvaluationMethod.BOOLEAN
                || method == EvaluationMethod.MULTI_CONDITION;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean looksNumeric(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("km") || lower.contains("tier") || lower.contains("iso")
            || lower.contains("personel") || lower.contains("sla") || lower.contains("rto");
    }

    private RequirementCondition syntheticNumeric(String text) {
        if (text == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern
            .compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*(km|kilometre|kilometer)")
            .matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return RequirementCondition.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "NUMERIC", "DATA_CENTER_DISTANCE", ConditionOperator.GREATER_THAN_OR_EQUAL, null,
            new BigDecimal(matcher.group(1).replace(',', '.')), "km", null, null, 0, true,
            java.time.Instant.now());
    }

    private List<RequirementCapabilityMatcher.CapabilitySnapshot> loadCapabilities(
        UUID organizationId
    ) {
        return jdbc.query("""
            select id, capability_type, normalized_name, text_value, numeric_value, unit,
                   boolean_value, date_value, valid_until::date, scope_text, status
              from capability
             where organization_id = ?
               and coalesce(verification_status, 'UNVERIFIED') <> 'REVOKED'
            """, (rs, row) -> new RequirementCapabilityMatcher.CapabilitySnapshot(
                rs.getObject("id", UUID.class),
                rs.getString("capability_type"),
                rs.getString("normalized_name"),
                rs.getString("text_value"),
                rs.getBigDecimal("numeric_value"),
                rs.getString("unit"),
                rs.getObject("boolean_value") == null ? null : rs.getBoolean("boolean_value"),
                rs.getObject("date_value", LocalDate.class),
                rs.getObject("valid_until", LocalDate.class),
                rs.getString("scope_text"),
                rs.getString("status")
            ), organizationId);
    }

    private BigDecimal firstNumeric(List<Map<String, Object>> evidenceRows) {
        if (evidenceRows == null) {
            return null;
        }
        for (Map<String, Object> row : evidenceRows) {
            Object value = row.get("numericValue");
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            Object text = row.get("text");
            if (text != null) {
                var matcher = java.util.regex.Pattern
                    .compile("(\\d+(?:[.,]\\d+)?)\\s*(km|m)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(String.valueOf(text));
                if (matcher.find()) {
                    return new BigDecimal(matcher.group(1).replace(',', '.'));
                }
            }
        }
        return null;
    }

    private String firstUnit(List<Map<String, Object>> evidenceRows) {
        if (evidenceRows == null) {
            return null;
        }
        for (Map<String, Object> row : evidenceRows) {
            Object unit = row.get("unit");
            if (unit != null) {
                return String.valueOf(unit);
            }
            Object text = row.get("text");
            if (text != null && String.valueOf(text).toLowerCase(Locale.ROOT).contains("km")) {
                return "km";
            }
        }
        return null;
    }
}
