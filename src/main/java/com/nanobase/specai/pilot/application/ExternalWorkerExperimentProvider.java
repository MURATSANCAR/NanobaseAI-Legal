package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExternalWorkerExperimentProvider implements ExperimentExecutionProvider {
    @Override
    public boolean supports(String experimentTypeCode) {
        return experimentTypeCode != null && !experimentTypeCode.isBlank();
    }

    @Override
    public ExecutionPlan plan(
        UUID experimentId,
        String experimentTypeCode,
        JsonNode runtimeConfiguration
    ) {
        return new ExecutionPlan("EXTERNAL_EVALUATION_WORKER",
            "experiment.requested.v1", runtimeConfiguration);
    }
}
