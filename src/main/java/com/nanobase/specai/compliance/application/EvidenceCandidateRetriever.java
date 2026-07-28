package com.nanobase.specai.compliance.application;

import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EvidenceCandidateRetriever {
    List<CandidateEvidence> retrieve(UUID organizationId, UUID requirementId,
                                     UUID targetEntityId, Instant entityCutoff,
                                     Instant evidenceCutoff, PolicyVersion policy);
}
