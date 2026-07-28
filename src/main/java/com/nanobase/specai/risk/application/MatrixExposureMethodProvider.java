package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.ExposureFactor;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.ScoredDimension;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MatrixExposureMethodProvider implements RiskExposureMethodProvider {
    @Override
    public boolean supports(String method) {
        return "MATRIX".equals(method);
    }

    @Override
    public ScoredDimension score(String dimension, RiskExposureContext context,
                                 JsonNode configuration) {
        String source = configuration.path("matrix").path(dimension + "Signal").asText();
        double input = ConfigurableRiskSignalEngine.clamp(
            context.signals().getOrDefault(source, 0d));
        return new ScoredDimension(input,
            List.of(new ExposureFactor(source, input, 1, input)));
    }

    @Override
    public double combine(ScoredDimension probability, ScoredDimension impact,
                          JsonNode configuration) {
        for (JsonNode cell : configuration.path("matrix").path("cells")) {
            if (probability.score() >= cell.path("probabilityMinimum").asDouble()
                && impact.score() >= cell.path("impactMinimum").asDouble()) {
                return ConfigurableRiskSignalEngine.clamp(cell.path("exposure").asDouble());
            }
        }
        return 0;
    }
}
