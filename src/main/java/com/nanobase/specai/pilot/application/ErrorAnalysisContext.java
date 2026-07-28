package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ErrorAnalysisContext(
    String feedbackType,
    String feedbackClassification,
    String severity,
    JsonNode signals,
    Map<String, Object> availableEvidence
) {
}
