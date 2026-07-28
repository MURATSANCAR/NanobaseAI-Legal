package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public interface EvaluationPolicyEngine {
    EvaluationResult evaluate(List<Map<String, Double>> caseMetrics, JsonNode qualityGates);

    record EvaluationResult(Map<String, Double> aggregateMetrics,
                            Map<String, Boolean> gateResults,
                            boolean passed) {
    }
}
