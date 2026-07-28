package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.ClauseSnapshot;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.Match;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PolicyDocumentChangeMatcher implements DocumentChangeMatcher {
    @Override
    public List<Match> match(List<ClauseSnapshot> base, List<ClauseSnapshot> target,
                             VersionedPolicy policy) {
        JsonNode configuration = policy.configuration().path("changeMatching");
        double minimum = configuration.path("minimumSimilarity").asDouble(1);
        Map<String, ClauseSnapshot> targetByNumber = new HashMap<>();
        target.stream().filter(clause -> clause.number() != null && !clause.number().isBlank())
            .forEach(clause -> targetByNumber.put(clause.number(), clause));
        Set<UUID> usedTargets = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        for (ClauseSnapshot left : base) {
            ClauseSnapshot selected = targetByNumber.get(left.number());
            double similarity = selected == null ? 0 : similarity(left, selected);
            if (selected == null || usedTargets.contains(selected.id())) {
                selected = target.stream()
                    .filter(candidate -> !usedTargets.contains(candidate.id()))
                    .map(candidate -> new Candidate(candidate, similarity(left, candidate)))
                    .filter(candidate -> candidate.score() >= minimum)
                    .max(Comparator.comparingDouble(Candidate::score))
                    .map(Candidate::clause).orElse(null);
                similarity = selected == null ? 0 : similarity(left, selected);
            }
            if (selected == null) {
                matches.add(match(left.id(), null,
                    configuration.path("removedConceptCode").asText(),
                    0, 1, "UNMATCHED_BASE"));
                continue;
            }
            usedTargets.add(selected.id());
            String code = left.contentHash().equals(selected.contentHash())
                ? configuration.path("unchangedConceptCode").asText()
                : configuration.path("modifiedConceptCode").asText();
            matches.add(match(left.id(), selected.id(), code, similarity,
                similarity, "STRUCTURAL_MATCH"));
        }
        for (ClauseSnapshot right : target) {
            if (!usedTargets.contains(right.id())) {
                matches.add(match(null, right.id(),
                    configuration.path("addedConceptCode").asText(),
                    0, 1, "UNMATCHED_TARGET"));
            }
        }
        return List.copyOf(matches);
    }

    private Match match(UUID left, UUID right, String code, double similarity,
                        double confidence, String method) {
        var attributes = JsonNodeFactory.instance.objectNode();
        attributes.put("matchingMethod", method);
        return new Match(left, right, code,
            ConfigurableRiskSignalEngine.clamp(similarity),
            ConfigurableRiskSignalEngine.clamp(confidence), attributes);
    }

    private double similarity(ClauseSnapshot left, ClauseSnapshot right) {
        if (left.contentHash().equals(right.contentHash())) {
            return 1;
        }
        Set<String> leftTokens = tokens(left.normalizedText());
        Set<String> rightTokens = tokens(right.normalizedText());
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(java.util.Arrays.asList(
            value.toLowerCase(java.util.Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")));
    }

    private record Candidate(ClauseSnapshot clause, double score) {
    }
}
