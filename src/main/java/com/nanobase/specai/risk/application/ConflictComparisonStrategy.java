package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;

public interface ConflictComparisonStrategy {
    boolean supports(ConflictComparisonContext context);
    ConflictComparisonResult compare(ConflictComparisonContext context);
}
