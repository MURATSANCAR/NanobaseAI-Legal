package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.RiskConfidenceResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.Map;

public interface RiskConfidencePolicyEngine {
    RiskConfidenceResult calculate(Map<String, Double> factors,
                                   VersionedPolicy policy);
}
