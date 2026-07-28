package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ConceptMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import com.nanobase.specai.analysis.application.AnalysisModels.ProfileInputs;
import com.nanobase.specai.analysis.application.AnalysisModels.PromptMaterial;
import com.nanobase.specai.analysis.application.AnalysisModels.TerminologyMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.UnitMatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The domain resolves all analysis behavior through this port. Implementations may use a
 * database, configuration service, or a versioned remote registry.
 */
public interface AnalysisCatalogPort {
    ProfileInputs resolveProfileInputs(UUID organizationId, String sector, String documentType,
                                       String language);
    PolicyDocument policy(UUID organizationId, UUID policyVersionId);
    JsonNode outputSchema(UUID organizationId, UUID outputSchemaVersionId);
    PromptMaterial prompt(UUID organizationId, UUID promptPackageVersionId);
    List<TerminologyMatch> terminologyMatches(UUID organizationId, List<UUID> catalogIds,
                                               String normalizedText);
    Optional<ConceptMatch> concept(UUID organizationId, UUID ontologyVersionId, String code);
    Optional<UnitMatch> unitByAlias(UUID organizationId, String language, String normalizedAlias);
    List<ModelCandidate> modelCandidates(UUID organizationId);
    JsonNode requirementGrid(UUID organizationId);

    List<Map<String, Object>> ontologies(UUID organizationId);
    List<Map<String, Object>> ontologyVersions(UUID organizationId, UUID ontologyId);
    List<Map<String, Object>> ontologyConcepts(UUID organizationId, UUID versionId);
    List<Map<String, Object>> terminologyCatalogs(UUID organizationId);
    UUID createCandidateTerm(UUID organizationId, UUID catalogId, String term, String termType,
                             String semanticRole, double weight, JsonNode metadata);
    void decideCandidateTerm(UUID organizationId, UUID entryId, boolean approved);
}
