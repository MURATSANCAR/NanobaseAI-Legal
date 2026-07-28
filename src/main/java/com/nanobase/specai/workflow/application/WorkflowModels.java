package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkflowModels {
    private WorkflowModels() {
    }

    public record WorkflowConditionContext(Map<String, Object> variables) {
        public WorkflowConditionContext {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }
    }

    public record ConditionEvaluationResult(boolean matched, List<String> diagnostics) {
        public ConditionEvaluationResult {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    public record WorkflowNodeExecutionContext(
        UUID organizationId,
        UUID workflowInstanceId,
        UUID tokenId,
        UUID workflowExecutionId,
        UUID nodeId,
        String nodeTypeConceptCode,
        JsonNode configuration,
        JsonNode input,
        Map<String, Object> variables
    ) {
    }

    public record WorkflowNodeExecutionResult(
        boolean completed,
        boolean waiting,
        JsonNode output,
        List<String> emittedEvents
    ) {
        public WorkflowNodeExecutionResult {
            emittedEvents = emittedEvents == null ? List.of() : List.copyOf(emittedEvents);
        }
    }

    public record WorkflowGraphNode(
        UUID id,
        String code,
        String typeConceptCode,
        JsonNode configuration
    ) {
    }

    public record WorkflowGraphTransition(
        UUID id,
        UUID sourceNodeId,
        UUID targetNodeId,
        JsonNode condition,
        int priority
    ) {
    }

    public record WorkflowSimulationInput(
        List<WorkflowGraphNode> nodes,
        List<WorkflowGraphTransition> transitions,
        WorkflowConditionContext conditionContext,
        Set<String> supportedNodeTypes,
        Set<String> authorizedNodeCodes,
        int maximumVisitsPerNode
    ) {
        public WorkflowSimulationInput {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
            supportedNodeTypes = supportedNodeTypes == null ? Set.of() : Set.copyOf(supportedNodeTypes);
            authorizedNodeCodes = authorizedNodeCodes == null ? Set.of() : Set.copyOf(authorizedNodeCodes);
            maximumVisitsPerNode = maximumVisitsPerNode < 1 ? 3 : maximumVisitsPerNode;
        }
    }

    public record SimulationFinding(String code, String severity, String nodeCode, String message) {
    }

    public record WorkflowSimulationResult(
        boolean valid,
        List<String> visitedNodeCodes,
        List<SimulationFinding> findings
    ) {
    }

    public record AssignmentCandidate(
        String userId,
        String groupId,
        Set<String> capabilityConceptCodes,
        Set<String> roleConceptCodes,
        String department,
        String location,
        double workload,
        boolean available,
        boolean conflictOfInterest,
        Map<String, Object> attributes
    ) {
        public AssignmentCandidate {
            capabilityConceptCodes = capabilityConceptCodes == null
                ? Set.of() : Set.copyOf(capabilityConceptCodes);
            roleConceptCodes = roleConceptCodes == null ? Set.of() : Set.copyOf(roleConceptCodes);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record AssignmentContext(
        UUID organizationId,
        UUID projectId,
        String taskTypeConceptCode,
        String priorityConceptCode,
        String previousReviewerId,
        String manualUserOverride,
        String manualGroupOverride,
        List<AssignmentCandidate> candidates,
        Map<String, Object> attributes
    ) {
        public AssignmentContext {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record AssignmentPolicyVersion(UUID id, JsonNode configuration) {
    }

    public record AssignmentResult(
        String assignedUserId,
        String assignedGroupId,
        List<String> explanation,
        boolean manualResolutionRequired
    ) {
    }

    public record ApprovalVote(
        String reviewerUserId,
        String decisionConceptCode,
        double weight
    ) {
    }

    public record ApprovalPolicyContext(
        String modeConceptCode,
        int eligibleReviewerCount,
        List<ApprovalVote> votes,
        Map<String, Object> attributes
    ) {
        public ApprovalPolicyContext {
            votes = votes == null ? List.of() : List.copyOf(votes);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record ApprovalPolicyResult(
        boolean completed,
        boolean approved,
        boolean rejected,
        int positiveCount,
        int negativeCount,
        double positiveWeight,
        List<String> explanation
    ) {
    }

    public record BusinessCalendarDefinition(
        String timezone,
        JsonNode configuration,
        Map<LocalDate, String> exceptions
    ) {
        public BusinessCalendarDefinition {
            exceptions = exceptions == null ? Map.of() : Map.copyOf(exceptions);
        }
    }

    public record SlaCalculationResult(
        Instant targetDueAt,
        Instant warningAt,
        Instant breachAt
    ) {
    }

    public record PolicyEvaluationResult(
        boolean passed,
        List<String> failedRuleCodes,
        List<String> warnings,
        Map<String, Object> facts
    ) {
        public PolicyEvaluationResult {
            failedRuleCodes = failedRuleCodes == null ? List.of() : List.copyOf(failedRuleCodes);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            facts = facts == null ? Map.of() : Map.copyOf(facts);
        }
    }

    public record DecisionSupportFactor(
        String factorConceptCode,
        double effectScore,
        double weight,
        String description,
        Map<String, Object> sourceReference
    ) {
        public DecisionSupportFactor {
            sourceReference = sourceReference == null ? Map.of() : Map.copyOf(sourceReference);
        }
    }

    public record DecisionSupportContext(
        Map<String, Object> verifiedFacts,
        List<DecisionSupportFactor> factors
    ) {
        public DecisionSupportContext {
            verifiedFacts = verifiedFacts == null ? Map.of() : Map.copyOf(verifiedFacts);
            factors = factors == null ? List.of() : List.copyOf(factors);
        }
    }

    public record DecisionSupportResult(
        String recommendedDecisionConceptCode,
        double confidence,
        boolean requiresExecutiveReview,
        List<DecisionSupportFactor> factors,
        List<String> explanation
    ) {
    }
}
