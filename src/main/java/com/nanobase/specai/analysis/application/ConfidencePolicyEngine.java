package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;

public interface ConfidencePolicyEngine {
    ConfidenceResult calculate(ConfidenceContext context, PolicyDocument policy);
}
