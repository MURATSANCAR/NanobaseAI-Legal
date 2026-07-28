package com.nanobase.specai.compliance.application;

import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;

public interface ComparisonStrategy {
    boolean supports(ComparisonContext context);

    ComparisonResult compare(ComparisonContext context);
}
