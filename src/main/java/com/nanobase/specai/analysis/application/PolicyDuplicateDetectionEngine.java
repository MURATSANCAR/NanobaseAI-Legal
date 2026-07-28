package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PolicyDuplicateDetectionEngine implements DuplicateDetectionEngine {
    @Override
    public DuplicateResult compare(DuplicateCandidate left, DuplicateCandidate right,
                                   PolicyDocument policy) {
        PolicyConfiguration configuration = new PolicyConfiguration(policy.configuration());
        Map<String, Double> weights = configuration.requiredWeights("duplicate.weights");
        double text = jaccard(tokens(left.normalizedText()), tokens(right.normalizedText()));
        double concept = left.conceptId() != null && left.conceptId().equals(right.conceptId())
            ? 1 : 0;
        double attributes = left.attributes() != null
            && left.attributes().equals(right.attributes()) ? 1 : 0;
        double source = left.sourceClauseId().equals(right.sourceClauseId()) ? 1 : 0;
        double score = text * required(weights, "text")
            + concept * required(weights, "concept")
            + attributes * required(weights, "attributes")
            + source * required(weights, "source");
        double total = weights.values().stream().mapToDouble(Math::abs).sum();
        score = total == 0 ? 0 : score / total;
        double likely = configuration.requiredNumber("duplicate.likely");
        double possible = configuration.requiredNumber("duplicate.possible");
        String status = score >= likely ? "LIKELY_DUPLICATE"
            : score >= possible ? "POSSIBLE_DUPLICATE" : "UNIQUE";
        return new DuplicateResult(status, score,
            List.of("NORMALIZED_TEXT", "ONTOLOGY_CONCEPT", "PARAMETER_STRUCTURE",
                "SOURCE_RELATION"));
    }

    private double required(Map<String, Double> weights, String code) {
        if (!weights.containsKey(code)) {
            throw new IllegalStateException("Duplicate weight is missing: " + code);
        }
        return weights.get(code);
    }

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value != null) {
            for (String token : value.split("[^\\p{L}\\p{N}]+")) {
                if (!token.isBlank()) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}
