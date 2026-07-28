package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionStrategy;
import com.nanobase.specai.analysis.domain.AnalysisProfile;

public interface ExtractionStrategyResolver {
    ExtractionStrategy resolve(ClauseAnalysisContext context, AnalysisProfile profile);
}
