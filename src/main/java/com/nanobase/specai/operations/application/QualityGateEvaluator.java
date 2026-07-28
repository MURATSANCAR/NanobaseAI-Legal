package com.nanobase.specai.operations.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QualityGateEvaluator {
    public Result evaluate(JsonNode configuration, Map<String, Double> metrics) {
        List<Violation> violations = new ArrayList<>();
        JsonNode conditions = configuration.path("conditions");
        if (!conditions.isArray() || conditions.isEmpty()) {
            return new Result(false, List.of(
                new Violation("GATE_CONFIGURATION", "conditions", 1, 0)));
        }
        for (JsonNode condition : conditions) {
            String metric = condition.path("metric").asText();
            String operator = condition.path("operator").asText();
            double threshold = condition.path("threshold").asDouble(Double.NaN);
            Double actual = metrics.get(metric);
            if (metric.isBlank() || Double.isNaN(threshold) || actual == null
                || !passes(operator, actual, threshold)) {
                violations.add(new Violation(metric.isBlank() ? "UNKNOWN" : metric,
                    operator, threshold, actual == null ? Double.NaN : actual));
            }
        }
        return new Result(violations.isEmpty(), List.copyOf(violations));
    }

    private boolean passes(String operator, double actual, double threshold) {
        return switch (operator) {
            case "GTE" -> actual >= threshold;
            case "GT" -> actual > threshold;
            case "LTE" -> actual <= threshold;
            case "LT" -> actual < threshold;
            default -> false;
        };
    }

    public record Result(boolean passed, List<Violation> violations) {
    }

    public record Violation(String metric, String operator, double threshold, double actual) {
    }
}
