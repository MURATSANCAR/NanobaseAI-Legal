package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.GroundingInput;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingResult;

public interface GroundingValidator {
    GroundingResult validate(GroundingInput input);
}
