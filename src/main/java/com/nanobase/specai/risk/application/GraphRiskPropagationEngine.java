package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.ImpactGraphEdge;
import com.nanobase.specai.risk.application.RiskModels.PropagationCandidate;
import com.nanobase.specai.risk.application.RiskModels.PropagationContext;
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
public class GraphRiskPropagationEngine implements RiskPropagationEngine {
    @Override
    public List<PropagationCandidate> propagate(PropagationContext context,
                                                VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        int maximumDepth = Math.max(1, configuration.path("maximumDepth").asInt(1));
        double minimum = configuration.path("minimumConfidence").asDouble(1);
        UUID conceptId = UUID.fromString(configuration.path("propagationConceptId").asText());
        String conceptCode = configuration.path("propagationConceptCode").asText();
        Map<UUID, List<ImpactGraphEdge>> adjacency = new HashMap<>();
        context.graph().forEach(edge ->
            adjacency.computeIfAbsent(edge.sourceId(), ignored -> new ArrayList<>()).add(edge));
        ArrayDeque<Path> queue = new ArrayDeque<>();
        queue.add(new Path(context.sourceEntityId(), "SOURCE", List.of(context.sourceEntityId()),
            1, 0));
        Set<UUID> seen = new HashSet<>();
        List<PropagationCandidate> candidates = new ArrayList<>();
        while (!queue.isEmpty()) {
            Path path = queue.removeFirst();
            if (!seen.add(path.id()) || path.depth() >= maximumDepth) {
                continue;
            }
            for (ImpactGraphEdge edge : adjacency.getOrDefault(path.id(), List.of())) {
                double confidence = path.confidence() * edge.confidence();
                if (confidence < minimum) {
                    continue;
                }
                List<UUID> ids = new ArrayList<>(path.ids());
                ids.add(edge.targetId());
                candidates.add(new PropagationCandidate(edge.targetType(), edge.targetId(),
                    conceptId, conceptCode, List.copyOf(ids), confidence));
                queue.add(new Path(edge.targetId(), edge.targetType(), List.copyOf(ids),
                    confidence, path.depth() + 1));
            }
        }
        return List.copyOf(candidates);
    }

    private record Path(UUID id, String type, List<UUID> ids,
                        double confidence, int depth) {
    }
}
