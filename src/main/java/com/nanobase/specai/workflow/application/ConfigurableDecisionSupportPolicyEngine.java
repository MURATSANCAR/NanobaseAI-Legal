package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportContext;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportFactor;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableDecisionSupportPolicyEngine implements DecisionSupportPolicyEngine {
    @Override
    public DecisionSupportResult evaluate(DecisionSupportContext context, JsonNode policy) {
        double weighted = context.factors().stream()
            .mapToDouble(factor -> factor.effectScore() * factor.weight()).sum();
        double totalWeight = context.factors().stream()
            .mapToDouble(factor -> Math.abs(factor.weight())).sum();
        double score = totalWeight == 0 ? 0 : weighted / totalWeight;
        JsonNode selected = null;
        for (JsonNode band : policy.path("decisionBands")) {
            if (score >= band.path("minimumScore").asDouble(-1)
                && score <= band.path("maximumScore").asDouble(1)) {
                selected = band;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Decision policy has no matching decision band");
        }
        double confidence = Math.max(0, Math.min(1,
            context.verifiedFacts().get("confidence") instanceof Number number
                ? number.doubleValue() : policy.path("defaultConfidence").asDouble(0)));
        List<String> explanation = new ArrayList<>();
        context.factors().stream()
            .sorted(Comparator.comparingDouble(
                (DecisionSupportFactor factor) -> Math.abs(factor.effectScore() * factor.weight()))
                .reversed())
            .limit(policy.path("maximumExplanationFactors").asInt(10))
            .forEach(factor -> explanation.add(factor.factorConceptCode() + ": "
                + factor.description()));
        return new DecisionSupportResult(selected.path("decisionConceptCode").asText(),
            confidence, selected.path("requiresExecutiveReview").asBoolean(true),
            context.factors(), List.copyOf(explanation));
    }
}
