package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.PropagationCandidate;
import com.nanobase.specai.risk.application.RiskModels.PropagationContext;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.List;

public interface RiskPropagationEngine {
    List<PropagationCandidate> propagate(PropagationContext context,
                                         VersionedPolicy policy);
}
