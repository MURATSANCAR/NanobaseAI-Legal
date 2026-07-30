package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticRequest;
import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceResult;
import com.nanobase.specai.compliance.application.ComplianceModels.EvidenceRerankingContext;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;
import com.nanobase.specai.compliance.application.ComplianceSemanticRouter.RoutingTrace;
import com.nanobase.specai.compliance.application.PreparedComplianceTask.DecisionSnapshot;
import com.nanobase.specai.compliance.application.PreparedComplianceTask.ExecutionPlan;
import com.nanobase.specai.compliance.application.PreparedComplianceTask.JobPolicySnapshot;
import com.nanobase.specai.compliance.application.PreparedComplianceTask.PrecomputedOutcome;
import com.nanobase.specai.compliance.application.PreparedComplianceTask.RequirementSnapshot;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepares a compliance matching task inside a short REQUIRES_NEW transaction.
 * Resolves the execution plan (PRECOMPUTED or SEMANTIC_MODEL) without calling the LLM.
 * Callers obtain a {@link PreparedComplianceTask} that is safe to hold across model execution
 * with no open transaction.
 */
@Service
public class ComplianceTaskPreparationService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceTaskPreparationService.class);

    private final TenantDatabaseContext tenantDatabase;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EvidenceCandidateRetriever retriever;
    private final EvidenceReranker reranker;
    private final ComparisonStrategyRegistry comparisons;
    private final PolicyComplianceConfidenceEngine confidenceEngine;
    private final DeterministicComplianceEvaluator deterministicEvaluator;
    private final AuditService audit;
    private final PlatformMetrics metrics;
    private final ComplianceJobTransactionService transactionService;

    public ComplianceTaskPreparationService(
        TenantDatabaseContext tenantDatabase,
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        EvidenceCandidateRetriever retriever,
        EvidenceReranker reranker,
        ComparisonStrategyRegistry comparisons,
        PolicyComplianceConfidenceEngine confidenceEngine,
        DeterministicComplianceEvaluator deterministicEvaluator,
        AuditService audit,
        PlatformMetrics metrics,
        ComplianceJobTransactionService transactionService
    ) {
        this.tenantDatabase = tenantDatabase;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.retriever = retriever;
        this.reranker = reranker;
        this.comparisons = comparisons;
        this.confidenceEngine = confidenceEngine;
        this.deterministicEvaluator = deterministicEvaluator;
        this.audit = audit;
        this.metrics = metrics;
        this.transactionService = transactionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedComplianceTask prepare(
        UUID organizationId,
        UUID jobId, UUID projectId,
        UUID analysisProfileId, UUID snapshotId,
        UUID retrievalPolicyVersionId, UUID matchingPolicyVersionId,
        UUID comparisonPolicyVersionId, UUID confidencePolicyVersionId,
        UUID promptPackageVersionId,
        UUID taskId, UUID requirementId, UUID targetEntityId,
        String workerId, long leaseGeneration, UUID correlationId, int maxOutputTokens
    ) {
        Instant prepareStarted = Instant.now();
        tenantDatabase.apply(organizationId);

        log.info("event=COMPLIANCE_TASK_PREPARE_STARTED jobId={} taskId={} requirementId={} "
                + "workerId={} correlationId={}",
            jobId, taskId, requirementId, workerId, correlationId);

        // Step 1 - cancellation guard; runs in its own nested REQUIRES_NEW so our TX stays open
        if (transactionService.cancellationState(organizationId, jobId).cancelRequested()) {
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_CANCELLED,
                "Task cancelled before preparation", 0);
        }

        // Step 2 - evidence retrieval and reranking
        PolicyVersion retrievalPolicy = retrievalPolicy(organizationId, retrievalPolicyVersionId);
        Snapshot snapshot = snapshot(organizationId, snapshotId);

        var retrievalTimer = metrics.retrievalStarted();
        List<CandidateEvidence> candidates;
        try {
            candidates = retriever.retrieve(
                organizationId, requirementId, targetEntityId,
                snapshot.entityCutoff(), snapshot.evidenceCutoff(), retrievalPolicy);
        } finally {
            metrics.retrievalCompleted(retrievalTimer);
        }
        metrics.retrievalCandidates(candidates.size());

        var rerankingTimer = metrics.rerankingStarted();
        RankedEvidenceResult ranked;
        try {
            ranked = reranker.rerank(
                new EvidenceRerankingContext(
                    organizationId, requirementId, targetEntityId, candidates, Map.of()),
                retrievalPolicy);
        } finally {
            metrics.rerankingCompleted(rerankingTimer);
        }

        // Persist retrieval counts before deciding execution plan
        jdbc.update("""
            update requirement_matching_task
               set candidate_count = ?, reranked_candidate_count = ?,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ? and organization_id = ?
            """, candidates.size(), ranked.ranked().size(), taskId, organizationId);

        // Step 3 - load requirement and shared lookup data
        Requirement requirement = requirement(organizationId, requirementId);
        List<Decision> allDecisions = decisions(organizationId);
        List<DecisionSnapshot> decisionSnapshots = allDecisions.stream()
            .map(d -> new DecisionSnapshot(d.id(), d.code()))
            .toList();
        JsonNode confidencePolicy = policyConfiguration(organizationId, confidencePolicyVersionId);
        String modelProfile = modelProfile(organizationId);

        // Steps 4-7 - determine execution plan
        List<Map<String, Object>> evidencePayload;
        EvaluationOutcome outcome;

        if (ranked.ranked().isEmpty()) {
            evidencePayload = List.of();
            EvaluationOutcome deterministic = tryDeterministic(
                organizationId, projectId, requirement, List.of());
            if (deterministic != null) {
                outcome = deterministic;
                metrics.complianceDeterministic();
                metrics.deterministicEvaluation();
            } else {
                outcome = missingOutcome(organizationId, confidencePolicyVersionId, requirement);
                metrics.complianceMissingEvidence();
            }
        } else {
            evidencePayload = selectedEvidencePayload(organizationId, ranked.ranked());
            EvaluationOutcome deterministic = tryDeterministic(
                organizationId, projectId, requirement, evidencePayload);
            if (deterministic != null) {
                outcome = deterministic;
                metrics.complianceDeterministic();
                metrics.deterministicEvaluation();
            } else {
                String provider = comparisonProvider(organizationId, requirement.attributes());
                if (provider != null && !"manual-only".equals(provider)) {
                    outcome = deterministicOutcome(
                        organizationId, confidencePolicyVersionId, requirement, ranked, provider);
                    metrics.complianceDeterministic();
                    metrics.comparisonStrategy(provider);
                } else {
                    outcome = null; // → SEMANTIC_MODEL
                }
            }
        }

        boolean contradictionHint = evidencePayload.stream()
            .anyMatch(item -> number(item.get("contradictionStrength")) > 0);

        SemanticRequest semanticRequest = null;
        PrecomputedOutcome precomputedOutcome = null;
        ExecutionPlan executionPlan;

        if (outcome != null) {
            precomputedOutcome = toPrecomputed(outcome);
            executionPlan = ExecutionPlan.PRECOMPUTED;
        } else {
            // Step 8 - build SemanticRequest for model execution phase
            executionPlan = ExecutionPlan.SEMANTIC_MODEL;
            metrics.complianceLlm();
            Prompt prompt = prompt(organizationId, promptPackageVersionId);
            semanticRequest = new SemanticRequest(
                organizationId, "nanobase-spec-ai", modelProfile,
                prompt.components(), prompt.schema(),
                Map.of("id", requirement.id(), "text", requirement.text(),
                    "attributes", requirement.attributes(),
                    "evaluationVersion", "v1"),
                ontology(organizationId, analysisProfileId), evidencePayload,
                allDecisions.stream().map(Decision::code).toList(),
                maxOutputTokens, correlationId);
        }

        // Step 9 - transition task to READY_FOR_MODEL; fence on claimed_by + lease_generation
        jdbc.update("""
            update requirement_matching_task
               set status = 'READY_FOR_MODEL',
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ? and organization_id = ?
               and claimed_by = ? and lease_generation = ?
               and status = 'RUNNING'
            """, taskId, organizationId, workerId, leaseGeneration);

        log.info("event=COMPLIANCE_TASK_PREPARE_COMPLETED jobId={} taskId={} "
                + "requirementId={} plan={} candidateCount={} rerankedCount={} "
                + "workerId={} correlationId={}",
            jobId, taskId, requirementId, executionPlan,
            candidates.size(), ranked.ranked().size(), workerId, correlationId);

        metrics.compliancePrepareDuration(Duration.between(prepareStarted, Instant.now()));

        JobPolicySnapshot policies = new JobPolicySnapshot(
            analysisProfileId, snapshotId,
            retrievalPolicyVersionId, matchingPolicyVersionId,
            comparisonPolicyVersionId, confidencePolicyVersionId,
            promptPackageVersionId);

        RequirementSnapshot requirementSnapshot = new RequirementSnapshot(
            requirement.id(), requirement.code(), requirement.text(),
            requirement.attributes(), requirement.evaluationMethod());

        return new PreparedComplianceTask(
            jobId, taskId, organizationId, projectId, requirementId, targetEntityId,
            workerId, leaseGeneration, correlationId == null ? null : correlationId.toString(),
            modelProfile, candidates.size(), ranked.ranked().size(),
            ranked, requirementSnapshot, policies, executionPlan,
            semanticRequest, decisionSnapshots, confidencePolicy,
            evidencePayload, contradictionHint, precomputedOutcome);
    }

    // -------------------------------------------------------------------------
    // Outcome helpers (mirror ComplianceAnalysisProcessor, no LLM call)
    // -------------------------------------------------------------------------

    private EvaluationOutcome tryDeterministic(
        UUID organizationId, UUID projectId,
        Requirement requirement, List<Map<String, Object>> evidencePayload
    ) {
        var decision = deterministicEvaluator.evaluate(
            organizationId, projectId,
            requirement.id(), requirement.text(), requirement.evaluationMethod(),
            evidencePayload);
        if (decision == null) {
            return null;
        }
        UUID decisionId = decisionByCode(organizationId, decision.decisionCode());
        if (decisionId == null) {
            return null;
        }
        ObjectNode summary = decision.summary();
        summary.put("decisionCode", decision.decisionCode());
        audit.recordSystem(organizationId, "system", "DETERMINISTIC_ASSESSMENT_COMPLETED",
            "Requirement", requirement.id(), null,
            Map.of("projectId", projectId, "decision", decision.decisionCode(),
                "evaluationSource", "DETERMINISTIC"));
        String gapHint = null;
        if ("NON_COMPLIANT".equals(decision.decisionCode())
            && decision.missingElements() != null
            && decision.missingElements().stream()
                .anyMatch(item -> item.toUpperCase().contains("NUMERIC"))) {
            gapHint = "NUMERIC_SHORTFALL";
        } else if ("NON_COMPLIANT".equals(decision.decisionCode())) {
            gapHint = "MISSING_CAPABILITY";
        } else if ("INSUFFICIENT_INFORMATION".equals(decision.decisionCode())) {
            gapHint = "INSUFFICIENT_EVIDENCE";
        }
        return new EvaluationOutcome(decisionId, decision.decisionCode(), summary, 0.95,
            decision.requiresReview(), null, List.of(), decision.contradiction(), null,
            "DETERMINISTIC", decision.missingElements(), false, gapHint);
    }

    private EvaluationOutcome deterministicOutcome(
        UUID organizationId, UUID confidencePolicyVersionId,
        Requirement requirement, RankedEvidenceResult ranked, String provider
    ) {
        RankedEvidence best = ranked.ranked().getFirst();
        CandidateEvidence evidence = best.evidence();
        JsonNode attributes = requirement.attributes();
        ComparisonContext context = new ComparisonContext(
            provider,
            attributes.path("operator").asText("GREATER_THAN_OR_EQUAL"),
            decimal(attributes, "requiredValue"),
            decimal(attributes, "requiredValueEnd"),
            uuid(attributes, "requiredUnitConceptId"),
            evidence.numericValue(),
            evidence.numericValueEnd(),
            evidence.unitConceptId(),
            booleanValue(attributes, "requiredBoolean"),
            evidence.booleanValue(),
            instant(attributes, "requiredDate"),
            evidence.metadata().get("validUntil") instanceof Instant value ? value : null,
            attributes.path("conditionExpression"),
            Map.of("organizationId", organizationId)
        );
        ComparisonResult comparison = comparisons.compare(context);
        String outcomeCode = switch (comparison.status()) {
            case "SATISFIED" -> "SATISFIED";
            case "NOT_SATISFIED" -> "NOT_SATISFIED";
            default -> "INDETERMINATE";
        };
        UUID decision = decisionByOutcome(organizationId, outcomeCode);
        JsonNode confidencePolicy = policyConfiguration(organizationId, confidencePolicyVersionId);
        boolean contradiction = "NOT_SATISFIED".equals(comparison.status());
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of(
                "relevance", best.score(),
                "validity", evidence.validityScore(),
                "authority", evidence.authorityScore(),
                "grounding", 1d,
                "deterministic", 1d,
                "entityResolution", evidence.entityId() == null ? 0.5 : 1d,
                "freshness", best.factors().getOrDefault("freshness", 0.5),
                "historicalAcceptance", evidence.historicalScore()
            ), false, contradiction, confidencePolicy));
        ObjectNode summary = mapper.createObjectNode();
        summary.set("comparison", comparison.explanation());
        summary.put("strategyProvider", provider);
        summary.put("status", comparison.status());
        summary.set("confidence", mapper.valueToTree(confidence));
        String decisionCode = switch (outcomeCode) {
            case "SATISFIED" -> "COMPLIANT";
            case "NOT_SATISFIED" -> "NON_COMPLIANT";
            default -> "INSUFFICIENT_INFORMATION";
        };
        return new EvaluationOutcome(decision, decisionCode, summary, confidence.score(),
            confidence.requiresReview(), null, List.of(best), contradiction, null,
            "DETERMINISTIC", List.of(), false,
            "NOT_SATISFIED".equals(comparison.status()) ? "NUMERIC_SHORTFALL" : null);
    }

    private EvaluationOutcome missingOutcome(
        UUID organizationId, UUID confidencePolicyVersionId, Requirement requirement
    ) {
        UUID decision = decisionByOutcome(organizationId, "INDETERMINATE");
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of(), true, false,
            policyConfiguration(organizationId, confidencePolicyVersionId)));
        ObjectNode summary = mapper.createObjectNode();
        summary.put("status", "MISSING_EVIDENCE");
        summary.put("requirementId", requirement.id().toString());
        summary.set("confidence", mapper.valueToTree(confidence));
        return new EvaluationOutcome(decision, "INSUFFICIENT_INFORMATION", summary,
            confidence.score(), true, null, List.of(), false, null, "HYBRID",
            List.of("MISSING_EVIDENCE"), false, "INSUFFICIENT_EVIDENCE");
    }

    private PrecomputedOutcome toPrecomputed(EvaluationOutcome outcome) {
        return new PrecomputedOutcome(
            outcome.decisionConceptId(),
            outcome.decisionCode(),
            outcome.summary(),
            outcome.confidence(),
            outcome.requiresReview(),
            outcome.modelRunId(),
            outcome.selectedEvidence(),
            outcome.contradiction(),
            outcome.routing(),
            outcome.evaluationSource(),
            outcome.missingElements(),
            outcome.ambiguousRequirement(),
            outcome.gapHint());
    }

    // -------------------------------------------------------------------------
    // DB query helpers (copied from ComplianceAnalysisProcessor)
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> selectedEvidencePayload(
        UUID organizationId, List<RankedEvidence> ranked
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RankedEvidence item : ranked) {
            Map<String, Object> row = jdbc.queryForMap("""
                select id, fragment_text as text, page_number,
                       valid_from, valid_until
                  from evidence_fragment
                 where id = ? and organization_id = ?
                """, item.evidence().fragmentId(), organizationId);
            Map<String, Object> value = new LinkedHashMap<>(row);
            value.put("relevanceScore", item.score());
            value.put("validityScore", item.evidence().validityScore());
            value.put("authorityScore", item.evidence().authorityScore());
            value.put("contradictionStrength", 0);
            payload.add(value);
        }
        return List.copyOf(payload);
    }

    private Snapshot snapshot(UUID organizationId, UUID id) {
        return jdbc.query("""
            select entity_version_cutoff, evidence_version_cutoff
              from knowledge_snapshot
             where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Knowledge snapshot is missing");
                }
                return new Snapshot(
                    result.getTimestamp(1).toInstant(),
                    result.getTimestamp(2).toInstant());
            }, id, organizationId);
    }

    private Requirement requirement(UUID organizationId, UUID id) {
        return jdbc.query("""
            select id, requirement_code, requirement_text, attributes_json::text,
                   evaluation_method
              from requirement
             where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Requirement not found");
                }
                return new Requirement(
                    result.getObject(1, UUID.class),
                    result.getString(2),
                    result.getString(3),
                    tree(result.getString(4)),
                    result.getString(5));
            }, id, organizationId);
    }

    private PolicyVersion retrievalPolicy(UUID organizationId, UUID id) {
        return jdbc.query("""
            select definition.policy_code, version.configuration_json::text
              from retrieval_policy_version version
              join retrieval_policy_definition definition
                on definition.id = version.policy_definition_id
             where version.id = ?
               and (version.organization_id = ? or version.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Retrieval policy snapshot is missing");
                }
                return new PolicyVersion(id, result.getString(1), tree(result.getString(2)));
            }, id, organizationId);
    }

    private JsonNode policyConfiguration(UUID organizationId, UUID id) {
        return jdbc.query("""
            select configuration_json::text
              from policy_version
             where id = ? and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Policy snapshot is missing");
                }
                return tree(result.getString(1));
            }, id, organizationId);
    }

    private String comparisonProvider(UUID organizationId, JsonNode attributes) {
        String strategyCode = attributes.path("comparisonStrategy").asText(null);
        if (strategyCode == null) {
            return null;
        }
        List<String> providers = jdbc.query("""
            select provider_code from comparison_strategy_definition
             where strategy_code = ? and active = true
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc limit 1
            """, (result, row) -> result.getString(1), strategyCode, organizationId);
        return providers.isEmpty() ? null : providers.getFirst();
    }

    private UUID decisionByOutcome(UUID organizationId, String outcome) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
             where concept_type = 'DECISION' and active = true
               and metadata_json ->> 'outcome' = ?
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class), outcome, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Decision concept missing for outcome " + outcome);
        }
        return ids.getFirst();
    }

    private UUID decisionByCode(UUID organizationId, String code) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
             where concept_type = 'DECISION' and active = true
               and concept_code = ?
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class), code, organizationId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private List<Decision> decisions(UUID organizationId) {
        return jdbc.query("""
            select id, concept_code from ontology_concept
             where concept_type = 'DECISION' and active = true
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc, sort_order
            """, (result, row) -> new Decision(
                result.getObject(1, UUID.class), result.getString(2)),
            organizationId);
    }

    private Prompt prompt(UUID organizationId, UUID versionId) {
        return jdbc.query("""
            select version.component_configuration_json::text,
                   schema.json_schema::text
              from prompt_package_version version
              join output_schema_version schema on schema.id = version.output_schema_id
             where version.id = ?
               and (version.organization_id = ? or version.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Compliance prompt snapshot is missing");
                }
                JsonNode config = tree(result.getString(1));
                List<String> components = new ArrayList<>();
                for (JsonNode code : config.path("components")) {
                    components.add(jdbc.queryForObject("""
                        select content_template from prompt_component
                         where component_code = ?
                           and (organization_id = ? or organization_id is null)
                         order by (organization_id is not null) desc limit 1
                        """, String.class, code.asText(), organizationId));
                }
                return new Prompt(List.copyOf(components), tree(result.getString(2)));
            }, versionId, organizationId);
    }

    private List<Map<String, Object>> ontology(UUID organizationId, UUID analysisProfileId) {
        return jdbc.queryForList("""
            select concept.concept_code as code, concept.name,
                   concept.concept_type as type, concept.metadata_json::text as metadata
              from analysis_profile profile
              join ontology_concept concept
                on concept.ontology_version_id = profile.ontology_version_id
             where profile.id = ? and profile.organization_id = ?
               and concept.active = true
               and (concept.organization_id = ? or concept.organization_id is null)
             order by concept.sort_order, concept.concept_code
            """, analysisProfileId, organizationId, organizationId);
    }

    private String modelProfile(UUID organizationId) {
        List<String> profiles = jdbc.query("""
            select profile_code from model_profile
             where active = true and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc,
                      case profile_code when 'BALANCED' then 0 when 'FAST' then 1 else 2 end,
                      profile_code
            """, (result, index) -> result.getString("profile_code"), organizationId);
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No active model profile configured");
        }
        return profiles.getFirst();
    }

    // -------------------------------------------------------------------------
    // Type-conversion helpers
    // -------------------------------------------------------------------------

    private double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    private Boolean booleanValue(JsonNode node, String field) {
        return node.path(field).isBoolean() ? node.path(field).booleanValue() : null;
    }

    private UUID uuid(JsonNode node, String field) {
        return node.path(field).isTextual()
            ? UUID.fromString(node.path(field).asText()) : null;
    }

    private Instant instant(JsonNode node, String field) {
        return node.path(field).isTextual()
            ? Instant.parse(node.path(field).asText()) : null;
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored compliance JSON is invalid", exception);
        }
    }

    // -------------------------------------------------------------------------
    // Local records
    // -------------------------------------------------------------------------

    private record Snapshot(Instant entityCutoff, Instant evidenceCutoff) {
    }

    private record Requirement(UUID id, String code, String text, JsonNode attributes,
                               String evaluationMethod) {
    }

    private record Decision(UUID id, String code) {
    }

    private record Prompt(List<String> components, JsonNode schema) {
    }

    private record EvaluationOutcome(
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
