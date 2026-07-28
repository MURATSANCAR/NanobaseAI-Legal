package com.nanobase.specai.pilot.application;

import java.util.List;

public record RootCauseAnalysisResult(
    String primaryCauseConcept,
    double confidence,
    List<ContributingFactor> contributingFactors,
    List<String> recommendedInvestigationAreas,
    String explanation,
    boolean humanApprovalRequired
) {
    public record ContributingFactor(String concept, double effect, String evidence) {
    }
}
