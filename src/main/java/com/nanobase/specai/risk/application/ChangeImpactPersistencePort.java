package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.Match;
import com.nanobase.specai.risk.application.RiskModels.AffectedEntity;
import com.nanobase.specai.risk.application.RiskModels.ChangeItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChangeImpactPersistencePort {
    UUID createChangeSet(UUID organizationId, UUID projectId, UUID baseVersionId,
                         UUID targetVersionId, UUID policyVersionId);
    void saveMatches(UUID organizationId, UUID changeSetId, List<Match> matches,
                     java.util.function.Function<String, UUID> conceptResolver);
    void completeChangeSet(UUID organizationId, UUID changeSetId, JsonNode summary);
    Map<String, Object> changeSet(UUID organizationId, UUID changeSetId);
    List<Map<String, Object>> changeItems(UUID organizationId, UUID changeSetId);
    List<ChangeItem> changeItemModels(UUID organizationId, UUID changeSetId);
    void correctMatch(UUID organizationId, UUID itemId, UUID baseClauseId,
                      UUID targetClauseId, UUID changeTypeConceptId,
                      String reviewStatus, JsonNode attributes);
    UUID createImpactJob(UUID organizationId, UUID projectId, UUID changeSetId,
                         UUID impactPolicyVersionId, int totalItems);
    void completeImpactJob(UUID organizationId, UUID jobId,
                           List<AffectedEntity> affectedEntities);
    Map<String, Object> impactJob(UUID organizationId, UUID jobId);
    List<Map<String, Object>> impactResults(UUID organizationId, UUID jobId);
    List<Map<String, Object>> impactEvents(UUID organizationId, UUID jobId);
    void markStale(UUID organizationId, List<AffectedEntity> affectedEntities,
                   UUID statusConceptId, UUID triggerConceptId, UUID triggerEntityId);
}
