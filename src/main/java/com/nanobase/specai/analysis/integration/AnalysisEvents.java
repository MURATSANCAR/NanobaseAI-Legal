package com.nanobase.specai.analysis.integration;

import java.util.UUID;

public final class AnalysisEvents {
    private AnalysisEvents() {
    }

    public record ExtractionRequested(UUID jobId, UUID documentId, UUID documentVersionId,
                                      UUID analysisProfileId) {
    }

    public record AnalysisProgress(UUID jobId, String status, int progress,
                                   int processedClauses, int extractedRequirements,
                                   int manualReviews) {
    }
}
