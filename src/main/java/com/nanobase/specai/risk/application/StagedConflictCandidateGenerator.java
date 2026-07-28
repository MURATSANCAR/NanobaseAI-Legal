package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidate;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateResult;
import com.nanobase.specai.risk.application.RiskModels.ConflictEntity;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class StagedConflictCandidateGenerator implements ConflictCandidateGenerator {
    @Override
    public ConflictCandidateResult generate(ConflictCandidateContext context,
                                            VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        int limit = Math.max(1, configuration.path("candidateLimit").asInt(100));
        double minimum = configuration.path("minimumRetrievalScore").asDouble(0);
        JsonNode weights = configuration.path("stageWeights");
        double scopeWeight = nonNegative(weights.path("entityScope").asDouble());
        double conceptWeight = nonNegative(weights.path("ontologyConcept").asDouble());
        double attributeWeight = nonNegative(weights.path("attribute").asDouble());
        double versionWeight = nonNegative(weights.path("version").asDouble());
        double totalWeight = scopeWeight + conceptWeight + attributeWeight + versionWeight;
        if (totalWeight == 0) {
            throw new IllegalArgumentException("Conflict candidate stageWeights are required");
        }
        List<ConflictCandidate> candidates = new ArrayList<>();
        for (ConflictEntity entity : context.retrievedCandidates()) {
            if (entity.id().equals(context.seed().id())
                || !Objects.equals(entity.scopeKey(), context.seed().scopeKey())) {
                continue;
            }
            List<String> stages = new ArrayList<>();
            stages.add("ENTITY_SCOPE");
            double score = scopeWeight;
            if (Objects.equals(entity.conceptId(), context.seed().conceptId())) {
                score += conceptWeight;
                stages.add("ONTOLOGY_CONCEPT");
            }
            double structured = structuredOverlap(context.seed().attributes(), entity.attributes());
            if (structured > 0) {
                score += attributeWeight * structured;
                stages.add("ATTRIBUTE");
            }
            if (!Objects.equals(entity.documentVersionId(), context.seed().documentVersionId())) {
                score += versionWeight;
                stages.add("VERSION");
            }
            score = ConfigurableRiskSignalEngine.clamp(score / totalWeight);
            if (score >= minimum) {
                stages.add("RERANK");
                candidates.add(new ConflictCandidate(context.seed(), entity, score,
                    List.copyOf(stages)));
            }
        }
        candidates.sort(Comparator.comparingDouble(ConflictCandidate::retrievalScore).reversed());
        List<ConflictCandidate> limited = candidates.stream().limit(limit).toList();
        return new ConflictCandidateResult(limited, context.retrievedCandidates().size(),
            limited.size());
    }

    private double structuredOverlap(JsonNode left, JsonNode right) {
        if (!left.isObject() || !right.isObject()) {
            return 0;
        }
        int shared = 0;
        int total = 0;
        var names = left.fieldNames();
        while (names.hasNext()) {
            total++;
            if (right.has(names.next())) {
                shared++;
            }
        }
        return total == 0 ? 0 : (double) shared / total;
    }

    private double nonNegative(double value) {
        return Math.max(0, value);
    }
}
