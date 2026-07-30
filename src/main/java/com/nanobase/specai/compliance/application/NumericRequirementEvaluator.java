package com.nanobase.specai.compliance.application;

import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Deterministic numeric requirement evaluation. Does not invent missing evidence values.
 */
@Service
public class NumericRequirementEvaluator {

    public enum Decision {
        COMPLIANT,
        NON_COMPLIANT,
        INSUFFICIENT_INFORMATION
    }

    public record EvaluationResult(
        Decision decision,
        BigDecimal normalizedEvidence,
        BigDecimal normalizedExpected,
        String unit,
        String reasonCode
    ) {
    }

    public EvaluationResult evaluate(RequirementCondition condition, BigDecimal evidenceValue,
                                     String evidenceUnit) {
        if (condition == null || condition.expectedNumericValue() == null) {
            return new EvaluationResult(Decision.INSUFFICIENT_INFORMATION, null, null, null,
                "MISSING_EXPECTED_VALUE");
        }
        if (evidenceValue == null) {
            return new EvaluationResult(Decision.INSUFFICIENT_INFORMATION, null,
                condition.expectedNumericValue(), condition.expectedUnit(),
                "MISSING_EVIDENCE_VALUE");
        }
        Optional<UnitPair> normalized = normalize(evidenceValue, evidenceUnit,
            condition.expectedNumericValue(), condition.expectedUnit());
        if (normalized.isEmpty()) {
            return new EvaluationResult(Decision.INSUFFICIENT_INFORMATION, evidenceValue,
                condition.expectedNumericValue(), condition.expectedUnit(),
                "UNIT_AMBIGUOUS");
        }
        UnitPair pair = normalized.get();
        boolean satisfied = compare(pair.evidence(), pair.expected(), condition.operator());
        return new EvaluationResult(
            satisfied ? Decision.COMPLIANT : Decision.NON_COMPLIANT,
            pair.evidence(), pair.expected(), pair.canonicalUnit(),
            satisfied ? "THRESHOLD_MET" : "THRESHOLD_NOT_MET");
    }

    private boolean compare(BigDecimal evidence, BigDecimal expected, ConditionOperator operator) {
        int cmp = evidence.compareTo(expected);
        return switch (operator) {
            case GREATER_THAN -> cmp > 0;
            case GREATER_THAN_OR_EQUAL, EXISTS -> cmp >= 0;
            case LESS_THAN -> cmp < 0;
            case LESS_THAN_OR_EQUAL -> cmp <= 0;
            case EQUALS -> cmp == 0;
            case NOT_EQUALS -> cmp != 0;
            default -> throw new IllegalArgumentException(
                "Operator not supported for numeric evaluation: " + operator);
        };
    }

    private Optional<UnitPair> normalize(BigDecimal evidence, String evidenceUnit,
                                         BigDecimal expected, String expectedUnit) {
        String left = canonicalize(evidenceUnit);
        String right = canonicalize(expectedUnit);
        if (left == null && right == null) {
            return Optional.of(new UnitPair(evidence, expected, null));
        }
        if (left == null || right == null) {
            return Optional.empty();
        }
        if (left.equals(right)) {
            return Optional.of(new UnitPair(evidence, expected, left));
        }
        Conversion factor = conversion(left, right);
        if (factor == null) {
            return Optional.empty();
        }
        BigDecimal convertedEvidence = evidence.multiply(factor.toTarget())
            .setScale(8, RoundingMode.HALF_UP);
        return Optional.of(new UnitPair(convertedEvidence, expected, right));
    }

    private String canonicalize(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "m", "metre", "meter", "meters", "metres" -> "m";
            case "km", "kilometer", "kilometre", "kilometers", "kilometres" -> "km";
            case "min", "minute", "minutes" -> "min";
            case "h", "hr", "hour", "hours", "saat" -> "h";
            case "d", "day", "days", "gün", "gun" -> "d";
            case "mo", "month", "months", "ay" -> "mo";
            case "y", "yr", "year", "years", "yıl", "yil" -> "y";
            case "gb" -> "gb";
            case "tb" -> "tb";
            case "%", "percent", "percentage", "yüzde", "yuzde" -> "%";
            default -> u;
        };
    }

    private Conversion conversion(String from, String to) {
        Map<String, BigDecimal> toMeter = Map.of(
            "m", BigDecimal.ONE,
            "km", BigDecimal.valueOf(1000));
        Map<String, BigDecimal> toMinute = Map.of(
            "min", BigDecimal.ONE,
            "h", BigDecimal.valueOf(60),
            "d", BigDecimal.valueOf(1440));
        Map<String, BigDecimal> toMonth = Map.of(
            "mo", BigDecimal.ONE,
            "y", BigDecimal.valueOf(12));
        Map<String, BigDecimal> toGb = Map.of(
            "gb", BigDecimal.ONE,
            "tb", BigDecimal.valueOf(1024));
        if (toMeter.containsKey(from) && toMeter.containsKey(to)) {
            return new Conversion(toMeter.get(from).divide(toMeter.get(to), 12, RoundingMode.HALF_UP));
        }
        if (toMinute.containsKey(from) && toMinute.containsKey(to)) {
            return new Conversion(toMinute.get(from).divide(toMinute.get(to), 12, RoundingMode.HALF_UP));
        }
        if (toMonth.containsKey(from) && toMonth.containsKey(to)) {
            return new Conversion(toMonth.get(from).divide(toMonth.get(to), 12, RoundingMode.HALF_UP));
        }
        if (toGb.containsKey(from) && toGb.containsKey(to)) {
            return new Conversion(toGb.get(from).divide(toGb.get(to), 12, RoundingMode.HALF_UP));
        }
        if ("%".equals(from) && "%".equals(to)) {
            return new Conversion(BigDecimal.ONE);
        }
        return null;
    }

    private record UnitPair(BigDecimal evidence, BigDecimal expected, String canonicalUnit) {
    }

    private record Conversion(BigDecimal toTarget) {
    }
}
