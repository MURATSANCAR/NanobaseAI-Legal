package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface ExperimentExecutionProvider {
    boolean supports(String experimentTypeCode);

    ExecutionPlan plan(UUID experimentId, String experimentTypeCode, JsonNode runtimeConfiguration);

    record ExecutionPlan(String providerCode, String routingKey, JsonNode runtimeConfiguration) {
    }
}
