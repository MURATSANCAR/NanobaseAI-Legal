package com.nanobase.specai.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualityGateEvaluatorTest {
    private final QualityGateEvaluator evaluator = new QualityGateEvaluator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresEveryQualityLatencyAndRegressionCondition() throws Exception {
        var configuration = mapper.readTree("""
            {"conditions":[
              {"metric":"criticalRequirementRecall","operator":"GTE","threshold":0.95},
              {"metric":"groundingSuccessRate","operator":"GTE","threshold":0.98},
              {"metric":"p95LatencyMs","operator":"LTE","threshold":30000},
              {"metric":"manualReviewRate","operator":"LTE","threshold":0.25}
            ]}
            """);

        var result = evaluator.evaluate(configuration, Map.of(
            "criticalRequirementRecall", 0.96,
            "groundingSuccessRate", 0.99,
            "p95LatencyMs", 28000d,
            "manualReviewRate", 0.24));

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void blocksActivationWhenOneMetricRegresses() throws Exception {
        var configuration = mapper.readTree("""
            {"conditions":[
              {"metric":"numericAccuracy","operator":"GTE","threshold":0.98},
              {"metric":"conflictFalsePositiveRate","operator":"LTE","threshold":0.05}
            ]}
            """);

        var result = evaluator.evaluate(configuration, Map.of(
            "numericAccuracy", 0.97,
            "conflictFalsePositiveRate", 0.04));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).extracting(
            QualityGateEvaluator.Violation::metric).containsExactly("numericAccuracy");
    }
}
