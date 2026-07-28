package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.AmbiguityContext;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;

public interface AmbiguityAnalysisEngine {
    AmbiguityResult analyze(AmbiguityContext context, VersionedPolicy policy);
}
