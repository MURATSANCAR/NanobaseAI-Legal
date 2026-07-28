package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.ChangeSet;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisContext;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;

public interface ImpactAnalysisEngine {
    ImpactAnalysisResult analyze(ChangeSet changeSet, ImpactAnalysisContext context,
                                 VersionedPolicy policy);
}
