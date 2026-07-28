package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultConflictStrategyRegistry implements ConflictStrategyRegistry {
    private final List<ConflictComparisonStrategy> strategies;

    public DefaultConflictStrategyRegistry(List<ConflictComparisonStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    @Override
    public ConflictComparisonResult compare(ConflictComparisonContext context) {
        return strategies.stream().filter(strategy -> strategy.supports(context)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No registered conflict comparison strategy supports this candidate"))
            .compare(context);
    }
}
