package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;

public interface ConflictCandidateGenerator {
    ConflictCandidateResult generate(ConflictCandidateContext context,
                                     VersionedPolicy policy);
}
