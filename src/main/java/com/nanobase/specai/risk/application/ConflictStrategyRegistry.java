package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;

public interface ConflictStrategyRegistry {
    ConflictComparisonResult compare(ConflictComparisonContext context);
}
