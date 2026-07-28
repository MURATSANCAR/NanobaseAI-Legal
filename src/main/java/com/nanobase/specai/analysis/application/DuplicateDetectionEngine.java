package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;

public interface DuplicateDetectionEngine {
    DuplicateResult compare(DuplicateCandidate left, DuplicateCandidate right,
                            PolicyDocument policy);
}
