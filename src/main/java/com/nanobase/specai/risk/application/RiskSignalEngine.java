package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.RiskSignalContext;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;

public interface RiskSignalEngine {
    RiskSignalResult evaluate(RiskSignalContext context, VersionedPolicy policy);
}
