package com.nanobase.specai.pilot.application;

public interface ErrorRootCauseAnalyzer {
    RootCauseAnalysisResult analyze(
        ErrorAnalysisContext context,
        ErrorAnalysisPolicyVersion policy
    );
}
