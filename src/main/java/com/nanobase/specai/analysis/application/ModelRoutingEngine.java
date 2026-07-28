package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import java.util.UUID;

public interface ModelRoutingEngine {
    ModelRoutingResult route(UUID organizationId, ModelRoutingContext context,
                             PolicyDocument policy);
}
