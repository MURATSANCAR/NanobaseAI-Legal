package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.EvidenceRerankingContext;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PolicyEvidenceReranker implements EvidenceReranker {
    @Override
    public RankedEvidenceResult rerank(EvidenceRerankingContext context,
                                       PolicyVersion policy) {
        JsonNode configuration = policy.configuration();
        JsonNode weights = configuration.path("signals");
        double minimumValidity = configuration.path("minimumValidityScore").asDouble(0);
        int limit = configuration.path("candidateLimits").path("reranking").asInt(15);
        List<RankedEvidence> ranked = context.candidates().stream()
            .filter(candidate -> candidate.validityScore() >= minimumValidity)
            .map(candidate -> score(candidate, weights, context.runtimeSignals()))
            .sorted(Comparator.comparingDouble(RankedEvidence::score).reversed()
                .thenComparing(item -> item.evidence().fragmentId()))
            .limit(limit)
            .toList();
        return new RankedEvidenceResult(ranked, context.candidates().size() - ranked.size());
    }

    private RankedEvidence score(CandidateEvidence candidate, JsonNode weights,
                                 Map<String, Double> runtimeSignals) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("ontology", candidate.ontologyScore());
        values.put("lexical", candidate.lexicalScore());
        values.put("attribute", candidate.attributeScore());
        values.put("evidenceValidity", candidate.validityScore());
        values.put("authority", candidate.authorityScore());
        values.put("historicalFeedback", candidate.historicalScore());
        values.put("relationDistance", 1d / (1 + Math.max(0, candidate.relationDistance())));
        values.put("freshness", freshness(candidate.documentDate()));
        if (runtimeSignals != null) {
            runtimeSignals.forEach(values::putIfAbsent);
        }
        double sum = 0;
        double total = 0;
        Map<String, Double> effects = new LinkedHashMap<>();
        for (Map.Entry<String, Double> signal : values.entrySet()) {
            double weight = weights.path(signal.getKey()).asDouble(0);
            double effect = clamp(signal.getValue()) * weight;
            effects.put(signal.getKey(), effect);
            sum += effect;
            total += Math.abs(weight);
        }
        return new RankedEvidence(candidate, total == 0 ? 0 : clamp(sum / total),
            Map.copyOf(effects));
    }

    private double freshness(Instant documentDate) {
        if (documentDate == null) {
            return 0.5;
        }
        long days = Math.max(0, Duration.between(documentDate, Instant.now()).toDays());
        return 1d / (1 + days / 365d);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
