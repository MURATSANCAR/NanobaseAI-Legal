package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.ScoredDimension;

public interface RiskExposureMethodProvider {
    boolean supports(String method);
    ScoredDimension score(String dimension, RiskExposureContext context, JsonNode configuration);
    double combine(ScoredDimension probability, ScoredDimension impact,
                   JsonNode configuration);
}
