package com.nanobase.specai.pilot.application;

import java.util.Map;

public record ErrorAnalysisPolicyVersion(
    String policyCode,
    int version,
    Map<String, Double> signalWeights,
    double minimumSuggestionConfidence
) {
}
