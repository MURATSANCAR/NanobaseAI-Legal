package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyContext;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyResult;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalVote;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableApprovalPolicyEngine implements ApprovalPolicyEngine {
    @Override
    public boolean supports(String approvalModeConceptCode) {
        return true;
    }

    @Override
    public ApprovalPolicyResult evaluate(ApprovalPolicyContext context,
                                         JsonNode configuration) {
        Set<String> positive = values(configuration.path("positiveDecisionConcepts"));
        Set<String> negative = values(configuration.path("negativeDecisionConcepts"));
        long positiveCount = context.votes().stream()
            .filter(vote -> positive.contains(vote.decisionConceptCode())).count();
        long negativeCount = context.votes().stream()
            .filter(vote -> negative.contains(vote.decisionConceptCode())).count();
        double positiveWeight = context.votes().stream()
            .filter(vote -> positive.contains(vote.decisionConceptCode()))
            .mapToDouble(ApprovalVote::weight).sum();
        double negativeWeight = context.votes().stream()
            .filter(vote -> negative.contains(vote.decisionConceptCode()))
            .mapToDouble(ApprovalVote::weight).sum();

        String aggregation = configuration.path("aggregation").asText("COUNT");
        double threshold = configuration.path("threshold").asDouble(1);
        double denominator = "PERCENTAGE".equals(aggregation)
            ? Math.max(1, context.eligibleReviewerCount()) : 1;
        double positiveScore = switch (aggregation) {
            case "WEIGHT" -> positiveWeight;
            case "PERCENTAGE" -> positiveCount / denominator;
            default -> positiveCount;
        };
        double negativeThreshold = configuration.path("negativeThreshold").asDouble(1);
        double negativeScore = "WEIGHT".equals(aggregation) ? negativeWeight : negativeCount;
        boolean rejected = negativeScore >= negativeThreshold;
        boolean approved = !rejected && positiveScore >= threshold;
        boolean allVotesRequired = configuration.path("allVotesRequired").asBoolean(false);
        boolean completed = rejected || approved && (!allVotesRequired
            || context.votes().size() >= context.eligibleReviewerCount());
        return new ApprovalPolicyResult(completed, approved, rejected,
            Math.toIntExact(positiveCount), Math.toIntExact(negativeCount), positiveWeight,
            List.of("Aggregation " + aggregation + " produced positive score "
                + positiveScore + " against threshold " + threshold));
    }

    private static Set<String> values(JsonNode node) {
        java.util.HashSet<String> values = new java.util.HashSet<>();
        node.forEach(item -> values.add(item.asText()));
        return Set.copyOf(values);
    }
}
