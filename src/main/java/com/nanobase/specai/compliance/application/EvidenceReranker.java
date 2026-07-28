package com.nanobase.specai.compliance.application;

import com.nanobase.specai.compliance.application.ComplianceModels.EvidenceRerankingContext;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;

public interface EvidenceReranker {
    RankedEvidenceResult rerank(EvidenceRerankingContext context, PolicyVersion policy);
}
