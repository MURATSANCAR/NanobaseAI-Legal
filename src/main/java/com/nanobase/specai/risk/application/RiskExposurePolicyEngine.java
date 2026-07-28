package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;

public interface RiskExposurePolicyEngine {
    RiskExposureResult calculate(RiskExposureContext context, VersionedPolicy policy);
}
