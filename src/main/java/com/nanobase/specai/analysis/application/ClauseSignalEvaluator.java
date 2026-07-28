package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalResult;

public interface ClauseSignalEvaluator {
    ClauseSignalResult evaluate(ClauseSignalContext context);
}
