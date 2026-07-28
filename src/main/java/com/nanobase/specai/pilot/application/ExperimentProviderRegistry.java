package com.nanobase.specai.pilot.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperimentProviderRegistry {
    private final List<ExperimentExecutionProvider> providers;

    public ExperimentProviderRegistry(List<ExperimentExecutionProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ExperimentExecutionProvider require(String experimentTypeCode) {
        List<ExperimentExecutionProvider> supported = providers.stream()
            .filter(provider -> provider.supports(experimentTypeCode)).toList();
        if (supported.size() != 1) {
            throw new IllegalStateException("Exactly one experiment provider is required for "
                + experimentTypeCode + "; found " + supported.size());
        }
        return supported.getFirst();
    }
}
