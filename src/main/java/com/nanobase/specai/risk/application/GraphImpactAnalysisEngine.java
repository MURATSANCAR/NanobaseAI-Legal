package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.AffectedEntity;
import com.nanobase.specai.risk.application.RiskModels.ChangeItem;
import com.nanobase.specai.risk.application.RiskModels.ChangeSet;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisContext;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisResult;
import com.nanobase.specai.risk.application.RiskModels.ImpactGraphEdge;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GraphImpactAnalysisEngine implements ImpactAnalysisEngine {
    @Override
    public ImpactAnalysisResult analyze(ChangeSet changeSet, ImpactAnalysisContext context,
                                        VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        int maximumDepth = Math.max(0, configuration.path("maximumDepth").asInt(0));
        double minimumConfidence = configuration.path("minimumConfidence").asDouble(1);
        UUID impactConceptId = UUID.fromString(
            configuration.path("impactConceptId").asText());
        String impactCode = configuration.path("impactConceptCode").asText();
        Map<UUID, List<ImpactGraphEdge>> adjacency = new HashMap<>();
        context.graph().forEach(edge ->
            adjacency.computeIfAbsent(edge.sourceId(), ignored -> new ArrayList<>()).add(edge));
        List<AffectedEntity> affected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ArrayDeque<Traversal> queue = new ArrayDeque<>();
        for (ChangeItem item : changeSet.items()) {
            UUID seed = item.targetId() == null ? item.sourceId() : item.targetId();
            String seedType = item.targetId() == null ? item.sourceType() : item.targetType();
            queue.add(new Traversal(seedType, seed, 0, item.confidence(),
                List.of("CHANGE_SET_ITEM")));
        }
        while (!queue.isEmpty()) {
            Traversal current = queue.removeFirst();
            String key = current.type() + ":" + current.id();
            if (!seen.add(key) || current.confidence() < minimumConfidence) {
                continue;
            }
            affected.add(new AffectedEntity(current.type(), current.id(),
                impactConceptId, impactCode, current.reasons(), current.confidence()));
            if (current.depth() >= maximumDepth) {
                continue;
            }
            for (ImpactGraphEdge edge : adjacency.getOrDefault(current.id(), List.of())) {
                List<String> reasons = new ArrayList<>(current.reasons());
                reasons.add(edge.dependencyConceptId().toString());
                queue.add(new Traversal(edge.targetType(), edge.targetId(),
                    current.depth() + 1, current.confidence() * edge.confidence(),
                    List.copyOf(reasons)));
            }
        }
        return new ImpactAnalysisResult(List.copyOf(affected));
    }

    private record Traversal(String type, UUID id, int depth, double confidence,
                             List<String> reasons) {
    }
}
