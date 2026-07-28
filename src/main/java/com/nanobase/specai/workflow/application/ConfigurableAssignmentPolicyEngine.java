package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentCandidate;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentContext;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentPolicyVersion;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableAssignmentPolicyEngine implements AssignmentPolicyEngine {
    @Override
    public AssignmentResult resolve(AssignmentContext context, AssignmentPolicyVersion policy) {
        if (context.manualUserOverride() != null || context.manualGroupOverride() != null) {
            return new AssignmentResult(context.manualUserOverride(), context.manualGroupOverride(),
                List.of("Audited manual override selected"), false);
        }
        JsonNode configuration = policy.configuration();
        Set<String> capabilities = textSet(configuration.path("requiredCapabilities"));
        Set<String> roles = textSet(configuration.path("requiredRoles"));
        double maximumWorkload = configuration.path("maximumWorkload").asDouble(1);
        String department = nullableText(configuration, "department");
        String location = nullableText(configuration, "location");
        boolean rejectConflicts = configuration.path("rejectConflictOfInterest").asBoolean(true);
        boolean preferPrevious = configuration.path("preferPreviousReviewer").asBoolean(false);

        List<ScoredCandidate> eligible = new ArrayList<>();
        for (AssignmentCandidate candidate : context.candidates()) {
            List<String> reasons = new ArrayList<>();
            if (!candidate.available() || candidate.workload() > maximumWorkload
                || rejectConflicts && candidate.conflictOfInterest()
                || !candidate.capabilityConceptCodes().containsAll(capabilities)
                || !candidate.roleConceptCodes().containsAll(roles)
                || department != null && !department.equals(candidate.department())
                || location != null && !location.equals(candidate.location())) {
                continue;
            }
            double score = (1 - Math.max(0, Math.min(1, candidate.workload())))
                * configuration.path("weights").path("availability").asDouble(1);
            if (preferPrevious && candidate.userId() != null
                && candidate.userId().equals(context.previousReviewerId())) {
                score += configuration.path("weights").path("previousReviewer").asDouble(1);
                reasons.add("Previous reviewer preference matched");
            }
            reasons.add("Matched configured capability, role and availability constraints");
            eligible.add(new ScoredCandidate(candidate, score, reasons));
        }
        return eligible.stream().max(Comparator.comparingDouble(ScoredCandidate::score))
            .map(selected -> new AssignmentResult(selected.candidate().userId(),
                selected.candidate().groupId(), selected.reasons(), false))
            .orElseGet(() -> new AssignmentResult(null,
                nullableText(configuration, "fallbackGroupId"),
                List.of("No eligible candidate matched the versioned policy"),
                !configuration.hasNonNull("fallbackGroupId")));
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return Set.copyOf(values);
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank()
            ? node.path(field).asText() : null;
    }

    private record ScoredCandidate(AssignmentCandidate candidate, double score,
                                   List<String> reasons) {
    }
}
