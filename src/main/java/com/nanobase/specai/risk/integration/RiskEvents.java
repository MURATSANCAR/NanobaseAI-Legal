package com.nanobase.specai.risk.integration;

import java.util.UUID;

public final class RiskEvents {
    private RiskEvents() {
    }

    public record RiskAnalysisRequested(UUID jobId, UUID projectId,
                                        UUID riskAnalysisProfileId) {
    }

    public record RiskAnalysisProgress(UUID jobId, String status, int progress,
                                       int processedCandidates, int risks,
                                       int ambiguities, int conflicts) {
    }
}
