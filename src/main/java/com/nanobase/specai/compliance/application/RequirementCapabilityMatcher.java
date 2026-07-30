package com.nanobase.specai.compliance.application;

import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RequirementCapabilityMatcher {
    private final NumericRequirementEvaluator numericEvaluator;
    private final ClosedWorldValidator closedWorldValidator;

    public RequirementCapabilityMatcher(NumericRequirementEvaluator numericEvaluator,
                                        ClosedWorldValidator closedWorldValidator) {
        this.numericEvaluator = numericEvaluator;
        this.closedWorldValidator = closedWorldValidator;
    }

    public enum MatchStatus {
        MATCHED,
        PARTIALLY_MATCHED,
        NOT_MATCHED,
        UNKNOWN,
        EXPIRED,
        OUT_OF_SCOPE
    }

    public record CapabilitySnapshot(
        UUID id,
        String capabilityType,
        String normalizedName,
        String textValue,
        BigDecimal numericValue,
        String unit,
        Boolean booleanValue,
        LocalDate dateValue,
        LocalDate validUntil,
        String scope,
        String status
    ) {
    }

    public record MatchResult(
        MatchStatus status,
        List<UUID> matchedConditionIds,
        List<UUID> missingConditionIds,
        List<UUID> contradictingConditionIds,
        double confidence,
        String evaluationMethod
    ) {
    }

    public MatchResult match(UUID organizationId, UUID projectId,
                             List<RequirementCondition> conditions,
                             List<CapabilitySnapshot> capabilities,
                             LocalDate asOf) {
        if (conditions == null || conditions.isEmpty()) {
            return new MatchResult(MatchStatus.UNKNOWN, List.of(), List.of(), List.of(), 0.0,
                "NO_CONDITIONS");
        }
        List<UUID> matched = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        List<UUID> contradicting = new ArrayList<>();
        boolean anyExpired = false;
        boolean anyOutOfScope = false;
        String method = "DETERMINISTIC";

        for (RequirementCondition condition : conditions) {
            CapabilitySnapshot hit = findCandidate(condition, capabilities);
            if (hit == null) {
                boolean closedWorld = closedWorldValidator.hasActiveDeclaration(
                    organizationId, projectId, condition.fieldName());
                if (closedWorld) {
                    missing.add(condition.id());
                    if (condition.mandatory()) {
                        contradicting.add(condition.id());
                    }
                } else {
                    missing.add(condition.id());
                }
                continue;
            }
            if (hit.validUntil() != null && asOf != null && hit.validUntil().isBefore(asOf)) {
                anyExpired = true;
                contradicting.add(condition.id());
                continue;
            }
            if (isOutOfScope(condition, hit)) {
                anyOutOfScope = true;
                missing.add(condition.id());
                continue;
            }
            ConditionOutcome outcome = evaluateCondition(condition, hit);
            switch (outcome) {
                case MATCHED -> matched.add(condition.id());
                case CONTRADICTING -> contradicting.add(condition.id());
                case MISSING -> missing.add(condition.id());
            }
        }

        if (anyExpired && matched.isEmpty()) {
            return new MatchResult(MatchStatus.EXPIRED, matched, missing, contradicting,
                confidence(matched, conditions), method);
        }
        if (anyOutOfScope && matched.isEmpty() && contradicting.isEmpty()) {
            return new MatchResult(MatchStatus.OUT_OF_SCOPE, matched, missing, contradicting,
                confidence(matched, conditions), method);
        }
        if (!contradicting.isEmpty() && matched.isEmpty()) {
            return new MatchResult(MatchStatus.NOT_MATCHED, matched, missing, contradicting,
                confidence(matched, conditions), method);
        }
        if (!missing.isEmpty() && !matched.isEmpty()) {
            return new MatchResult(MatchStatus.PARTIALLY_MATCHED, matched, missing, contradicting,
                confidence(matched, conditions), method);
        }
        if (!missing.isEmpty()) {
            boolean closedWorld = closedWorldValidator.hasActiveDeclaration(
                organizationId, projectId, null);
            return new MatchResult(
                closedWorld ? MatchStatus.NOT_MATCHED : MatchStatus.UNKNOWN,
                matched, missing, contradicting, confidence(matched, conditions), method);
        }
        if (matched.size() == conditions.size()) {
            return new MatchResult(MatchStatus.MATCHED, matched, missing, contradicting, 1.0, method);
        }
        return new MatchResult(MatchStatus.UNKNOWN, matched, missing, contradicting,
            confidence(matched, conditions), method);
    }

    private CapabilitySnapshot findCandidate(RequirementCondition condition,
                                             List<CapabilitySnapshot> capabilities) {
        String expected = normalize(condition.expectedValue());
        String field = normalize(condition.fieldName());
        for (CapabilitySnapshot capability : capabilities) {
            String name = normalize(capability.normalizedName());
            String type = normalize(capability.capabilityType());
            if (expected != null && (expected.equals(name)
                || expected.equals(normalize(capability.textValue())))) {
                return capability;
            }
            if (field != null && (field.equals(type) || field.equals(name))) {
                return capability;
            }
        }
        return null;
    }

    private ConditionOutcome evaluateCondition(RequirementCondition condition,
                                               CapabilitySnapshot capability) {
        if (condition.expectedNumericValue() != null
            || condition.operator() == ConditionOperator.GREATER_THAN
            || condition.operator() == ConditionOperator.GREATER_THAN_OR_EQUAL
            || condition.operator() == ConditionOperator.LESS_THAN
            || condition.operator() == ConditionOperator.LESS_THAN_OR_EQUAL) {
            var result = numericEvaluator.evaluate(condition, capability.numericValue(),
                capability.unit());
            return switch (result.decision()) {
                case COMPLIANT -> ConditionOutcome.MATCHED;
                case NON_COMPLIANT -> ConditionOutcome.CONTRADICTING;
                case INSUFFICIENT_INFORMATION -> ConditionOutcome.MISSING;
            };
        }
        if (condition.expectedBoolean() != null) {
            if (capability.booleanValue() == null) {
                return ConditionOutcome.MISSING;
            }
            boolean ok = Objects.equals(condition.expectedBoolean(), capability.booleanValue());
            if (condition.operator() == ConditionOperator.NOT_EQUALS) {
                ok = !ok;
            }
            return ok ? ConditionOutcome.MATCHED : ConditionOutcome.CONTRADICTING;
        }
        if (condition.operator() == ConditionOperator.EXISTS
            || condition.operator() == ConditionOperator.EQUALS) {
            return ConditionOutcome.MATCHED;
        }
        if (condition.operator() == ConditionOperator.NOT_EXISTS) {
            return ConditionOutcome.CONTRADICTING;
        }
        if (condition.operator() == ConditionOperator.VALID_ON_DATE
            || condition.operator() == ConditionOperator.BEFORE
            || condition.operator() == ConditionOperator.AFTER) {
            if (capability.dateValue() == null && capability.validUntil() == null) {
                return ConditionOutcome.MISSING;
            }
            LocalDate value = capability.validUntil() != null
                ? capability.validUntil() : capability.dateValue();
            LocalDate expected = condition.expectedDate();
            if (expected == null) {
                return ConditionOutcome.MISSING;
            }
            boolean ok = switch (condition.operator()) {
                case VALID_ON_DATE, AFTER -> !value.isBefore(expected);
                case BEFORE -> value.isBefore(expected);
                default -> false;
            };
            return ok ? ConditionOutcome.MATCHED : ConditionOutcome.CONTRADICTING;
        }
        return ConditionOutcome.MISSING;
    }

    private boolean isOutOfScope(RequirementCondition condition, CapabilitySnapshot capability) {
        if (capability.scope() == null || capability.scope().isBlank()) {
            return false;
        }
        if (condition.expectedValue() == null) {
            return false;
        }
        String scope = capability.scope().toLowerCase(Locale.ROOT);
        String expected = condition.expectedValue().toLowerCase(Locale.ROOT);
        return scope.contains("out of scope") || (expected.contains("scope:")
            && !scope.contains(expected.replace("scope:", "").trim()));
    }

    private double confidence(List<UUID> matched, List<RequirementCondition> conditions) {
        if (conditions.isEmpty()) {
            return 0.0;
        }
        return (double) matched.size() / (double) conditions.size();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum ConditionOutcome {
        MATCHED, MISSING, CONTRADICTING
    }
}
