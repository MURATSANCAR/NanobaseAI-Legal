package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.Optional;
import java.util.UUID;

public interface RiskCatalogPort {
    RiskProfileInputs resolveProfileInputs(UUID organizationId, UUID projectId);
    RiskProfileInputs profile(UUID organizationId, UUID profileId);
    VersionedPolicy policy(UUID organizationId, UUID versionId);
    JsonNode severityPolicy(UUID organizationId, UUID versionId);
    JsonNode authorityPolicy(UUID organizationId, UUID versionId);
    Optional<UUID> conceptId(UUID organizationId, UUID ontologyVersionId, String code);
    UUID activeClarificationStrategy(UUID organizationId);
    JsonNode riskGrid(UUID organizationId);
}
