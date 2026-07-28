package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.document.domain.Clause;

public interface ClauseContextBuilder {
    ClauseAnalysisContext build(Clause clause, AnalysisProfile profile);
}
