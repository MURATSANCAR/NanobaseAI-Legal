package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class GenericEvaluationPolicyEngine implements EvaluationPolicyEngine {
    @Override
    public EvaluationResult evaluate(List<Map<String, Double>> caseMetrics,
                                     JsonNode qualityGates) {
        if (caseMetrics == null || caseMetrics.isEmpty()) {
            throw new IllegalArgumentException("Evaluation dataset has no measured cases");
        }
        TreeSet<String> metricCodes = new TreeSet<>();
        caseMetrics.forEach(metrics -> metricCodes.addAll(metrics.keySet()));
        Map<String, Double> aggregate = new LinkedHashMap<>();
        for (String code : metricCodes) {
            aggregate.put(code, caseMetrics.stream()
                .filter(metrics -> metrics.containsKey(code))
                .mapToDouble(metrics -> metrics.get(code)).average()
                .orElseThrow());
        }
        Map<String, Boolean> gates = new LinkedHashMap<>();
        qualityGates.path("minimums").fields().forEachRemaining(gate -> {
            double actual = requiredMetric(aggregate, gate.getKey());
            gates.put("minimum:" + gate.getKey(),
                gate.getValue().isNumber() && actual >= gate.getValue().doubleValue());
        });
        qualityGates.path("maximums").fields().forEachRemaining(gate -> {
            double actual = requiredMetric(aggregate, gate.getKey());
            gates.put("maximum:" + gate.getKey(),
                gate.getValue().isNumber() && actual <= gate.getValue().doubleValue());
        });
        return new EvaluationResult(Map.copyOf(aggregate), Map.copyOf(gates),
            gates.values().stream().allMatch(Boolean::booleanValue));
    }

    private double requiredMetric(Map<String, Double> metrics, String code) {
        Double value = metrics.get(code);
        if (value == null) {
            throw new IllegalStateException("Quality gate references missing metric: " + code);
        }
        return value;
    }
}
