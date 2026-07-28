package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionRequest;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionResponse;

/**
 * Business code sees one logical local model. Runtime/provider selection stays behind this port.
 */
public interface AiGateway {
    String LOGICAL_MODEL = "nanobase-spec-ai";

    ExtractionResponse extract(ExtractionRequest request);
}
