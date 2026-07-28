package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PolicyModelRoutingEngine implements ModelRoutingEngine {
    private final AnalysisCatalogPort catalog;

    public PolicyModelRoutingEngine(AnalysisCatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public ModelRoutingResult route(UUID organizationId, ModelRoutingContext context,
                                    PolicyDocument policy) {
        JsonNode configuration = policy.configuration();
        boolean healthRequired = configuration.path("healthRequired").asBoolean(false);
        Map<String, JsonNode> configured = new LinkedHashMap<>();
        for (JsonNode profile : configuration.path("profiles")) {
            configured.put(profile.path("code").asText(), profile);
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        ModelCandidate selected = null;
        double selectedScore = -Double.MAX_VALUE;
        String requiredProfile = context.attributes().get("requiredProfile") == null
            ? null : String.valueOf(context.attributes().get("requiredProfile"));
        for (ModelCandidate candidate : catalog.modelCandidates(organizationId)) {
            JsonNode profile = configured.get(candidate.profileCode());
            if (profile == null
                || (requiredProfile != null
                    && !requiredProfile.equals(candidate.profileCode()))
                || (healthRequired
                && !"HEALTHY".equals(candidate.healthStatus()))) {
                continue;
            }
            double score = requiredNumber(profile, "baseScore");
            JsonNode signalWeights = profile.path("signalWeights");
            signalWeights.fields().forEachRemaining(weight -> {
                if (!weight.getValue().isNumber()) {
                    throw new IllegalStateException("Model routing signal weight must be numeric");
                }
            });
            for (Map.Entry<String, JsonNode> weight : iterable(signalWeights.fields())) {
                score += context.signals().getOrDefault(weight.getKey(), 0d)
                    * weight.getValue().doubleValue();
            }
            scores.put(candidate.profileCode(), score);
            if (score > selectedScore) {
                selected = candidate;
                selectedScore = score;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("No healthy model deployment matches routing policy");
        }
        List<String> reasons = new ArrayList<>();
        JsonNode reasonCodes = configured.get(selected.profileCode()).path("reasonCodes");
        if (reasonCodes.isArray()) {
            reasonCodes.forEach(code -> reasons.add(code.asText()));
        }
        if (reasons.isEmpty()) {
            reasons.add("HIGHEST_POLICY_SCORE");
        }
        return new ModelRoutingResult(selected.profileId(), selected.profileCode(),
            selected.deploymentId(), List.copyOf(reasons), Map.copyOf(scores));
    }

    private double requiredNumber(JsonNode node, String field) {
        if (!node.path(field).isNumber()) {
            throw new IllegalStateException("Model routing field is missing: " + field);
        }
        return node.path(field).doubleValue();
    }

    private <T> Iterable<T> iterable(java.util.Iterator<T> iterator) {
        return () -> iterator;
    }
}
