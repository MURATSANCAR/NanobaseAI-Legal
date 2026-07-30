package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticRequest;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;
import com.nanobase.specai.compliance.application.ComplianceSemanticRouter.RoutingTrace;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable prepare-phase snapshot. Contains no JPA entities, EntityManager, or
 * repository references. Safe to hold across model execution with no open TX.
 */
public record PreparedComplianceTask(
    UUID jobId,
    UUID taskId,
    UUID organizationId,
    UUID projectId,
    UUID requirementId,
    UUID targetEntityId,
    String workerId,
    long leaseGeneration,
    String correlationId,
    String modelProfile,
    int candidateCount,
    int rerankedCandidateCount,
    RankedEvidenceResult ranked,
    RequirementSnapshot requirement,
    JobPolicySnapshot policies,
    ExecutionPlan executionPlan,
    SemanticRequest semanticRequest,
    List<DecisionSnapshot> decisions,
    JsonNode confidencePolicy,
    List<Map<String, Object>> evidencePayload,
    boolean contradictionHint,
    PrecomputedOutcome precomputedOutcome
) {
    public boolean needsModelExecution() {
        return executionPlan == ExecutionPlan.SEMANTIC_MODEL && precomputedOutcome == null;
    }

    public enum ExecutionPlan {
        SEMANTIC_MODEL,
        PRECOMPUTED
    }

    public record RequirementSnapshot(
        UUID id,
        String code,
        String text,
        JsonNode attributes,
        String evaluationMethod
    ) {
    }

    public record JobPolicySnapshot(
        UUID analysisProfileId,
        UUID knowledgeSnapshotId,
        UUID retrievalPolicyVersionId,
        UUID matchingPolicyVersionId,
        UUID comparisonPolicyVersionId,
        UUID confidencePolicyVersionId,
        UUID promptPackageVersionId
    ) {
    }

    public record DecisionSnapshot(UUID id, String code) {
    }

    public record PrecomputedOutcome(
        UUID decisionConceptId,
        String decisionCode,
        JsonNode summary,
        double confidence,
        boolean requiresReview,
        UUID modelRunId,
        List<RankedEvidence> selectedEvidence,
        boolean contradiction,
        RoutingTrace routing,
        String evaluationSource,
        List<String> missingElements,
        boolean ambiguousRequirement,
        String gapHint
    ) {
    }
}
