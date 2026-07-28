package com.nanobase.specai.compliance.application;

import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ComparisonStrategyRegistry {
    private final List<ComparisonStrategy> strategies;

    public ComparisonStrategyRegistry(List<ComparisonStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public ComparisonResult compare(ComparisonContext context) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(context))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No active comparison provider supports the configured strategy"))
            .compare(context);
    }
}
