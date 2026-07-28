package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityCandidate;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityResolutionContext;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityResolutionResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PolicyEntityResolutionService implements EntityResolutionService {
    @Override
    public EntityResolutionResult resolve(EntityResolutionContext context,
                                          List<EntityCandidate> candidates) {
        JsonNode policy = context.policy();
        JsonNode weights = policy.path("weights");
        double confirmed = policy.path("confirmedThreshold").asDouble(0.92);
        double possible = policy.path("possibleThreshold").asDouble(0.72);
        double ambiguityDelta = policy.path("ambiguityDelta").asDouble(0.04);
        List<Scored> scored = candidates.stream()
            .filter(candidate -> context.organizationId() != null)
            .filter(candidate -> context.entityTypeConceptId() == null
                || context.entityTypeConceptId().equals(candidate.entityTypeConceptId()))
            .map(candidate -> score(context, candidate, weights))
            .sorted(Comparator.comparingDouble(Scored::total).reversed())
            .toList();
        if (scored.isEmpty() || scored.getFirst().total() < possible) {
            return new EntityResolutionResult("NEW_ENTITY", null,
                scored.isEmpty() ? 0 : scored.getFirst().total(),
                scored.isEmpty() ? Map.of() : scored.getFirst().signals(), List.of());
        }
        Scored best = scored.getFirst();
        List<UUID> ambiguous = new ArrayList<>();
        for (Scored item : scored) {
            if (best.total() - item.total() <= ambiguityDelta) {
                ambiguous.add(item.candidate().entityId());
            }
        }
        if (ambiguous.size() > 1) {
            return new EntityResolutionResult("AMBIGUOUS", null, best.total(),
                best.signals(), List.copyOf(ambiguous));
        }
        String status = best.total() >= confirmed ? "CONFIRMED_MATCH" : "POSSIBLE_MATCH";
        return new EntityResolutionResult(status, best.candidate().entityId(), best.total(),
            best.signals(), List.of());
    }

    private Scored score(EntityResolutionContext context, EntityCandidate candidate,
                         JsonNode weights) {
        Map<String, Double> signals = new LinkedHashMap<>();
        signals.put("normalizedName", similarity(normalize(context.name()),
            normalize(candidate.name())));
        signals.put("identifier", identifierMatch(context.identifiers(),
            candidate.identifiers()));
        signals.put("manufacturer", equal(context.manufacturerEntityId(),
            candidate.manufacturerEntityId()));
        signals.put("model", similarity(normalize(context.model()), normalize(candidate.model())));
        signals.put("version", similarity(normalize(context.version()),
            normalize(candidate.version())));
        signals.put("historical", clamp(candidate.historicalAcceptance()));
        double defaultWeight = signals.isEmpty() ? 0 : 1d / signals.size();
        double weighted = 0;
        double totalWeight = 0;
        for (Map.Entry<String, Double> signal : signals.entrySet()) {
            double weight = weights.path(signal.getKey()).asDouble(defaultWeight);
            weighted += signal.getValue() * weight;
            totalWeight += weight;
        }
        return new Scored(candidate, totalWeight == 0 ? 0 : weighted / totalWeight,
            Map.copyOf(signals));
    }

    private double identifierMatch(Map<String, String> left, Map<String, String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        return left.entrySet().stream().anyMatch(entry ->
            normalize(entry.getValue()).equals(normalize(right.get(entry.getKey())))) ? 1 : 0;
    }

    private double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0;
        }
        if (left.equals(right)) {
            return 1;
        }
        var leftTokens = SetSupport.tokens(left);
        var rightTokens = SetSupport.tokens(right);
        long intersection = leftTokens.stream().filter(rightTokens::contains).count();
        long union = leftTokens.size() + rightTokens.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private double equal(Object left, Object right) {
        return left != null && left.equals(right) ? 1 : 0;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\p{Alnum}]+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record Scored(EntityCandidate candidate, double total,
                          Map<String, Double> signals) {
    }

    private static final class SetSupport {
        private SetSupport() {
        }

        static java.util.Set<String> tokens(String value) {
            return java.util.Set.of(value.split("\\s+"));
        }
    }
}
