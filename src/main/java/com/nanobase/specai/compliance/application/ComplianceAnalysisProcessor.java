package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceAnalysisProcessor {
    private static final Logger log = LoggerFactory.getLogger(ComplianceAnalysisProcessor.class);
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

    private final TenantDatabaseContext tenantDatabase;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EvidenceCandidateRetriever retriever;
    private final EvidenceReranker reranker;
    private final ComparisonStrategyRegistry comparisons;
    private final PolicyComplianceConfidenceEngine confidenceEngine;
    private final ComplianceSemanticRouter semanticRouter;
    private final ComplianceJobService jobs;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;
    private final int maxOutputTokens;

    public ComplianceAnalysisProcessor(
        TenantDatabaseContext tenantDatabase,
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        EvidenceCandidateRetriever retriever,
        EvidenceReranker reranker,
        ComparisonStrategyRegistry comparisons,
        PolicyComplianceConfidenceEngine confidenceEngine,
        ComplianceSemanticRouter semanticRouter,
        ComplianceJobService jobs,
        OutboxService outbox,
        PlatformMetrics metrics,
        @org.springframework.beans.factory.annotation.Value(
            "${specai.ai-orchestrator.compliance-max-output-tokens:1024}") int maxOutputTokens
    ) {
        this.tenantDatabase = tenantDatabase;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.retriever = retriever;
        this.reranker = reranker;
        this.comparisons = comparisons;
        this.confidenceEngine = confidenceEngine;
        this.semanticRouter = semanticRouter;
        this.jobs = jobs;
        this.outbox = outbox;
        this.metrics = metrics;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
    }

    @Transactional
    public void process(UUID organizationId, UUID jobId, UUID correlationId) {
        tenantDatabase.apply(organizationId);
        Job job = job(organizationId, jobId);
        if (!"QUEUED".equals(job.status())) {
            return;
        }
        metrics.complianceAnalysis();
        jdbc.update("""
            update compliance_analysis_job
            set status = 'RUNNING', started_at = coalesce(started_at, now()),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, jobId, organizationId);
        jobs.event(organizationId, jobId, "STARTED", 0,
            "Compliance analysis started",
            Map.of("requirementCount", job.totalRequirementCount()));
        outbox.publish(organizationId, "ComplianceAnalysis", jobId,
            "ComplianceAnalysisStarted", "compliance.analysis.started.v1",
            Map.of("jobId", jobId), correlationId);
        List<Task> tasks = tasks(organizationId, jobId);
        int processed = 0;
        int completed = 0;
        int reviews = 0;
        int failed = 0;
        for (Task task : tasks) {
            try {
                TaskResult result = evaluate(organizationId, job, task, correlationId);
                completed++;
                if (result.requiresReview()) {
                    reviews++;
                }
            } catch (SemanticEvaluationException failure) {
                failed++;
                jdbc.update("""
                    update requirement_matching_task
                    set status = 'FAILED', error_code = ?, error_message = ?,
                        completed_at = clock_timestamp(), updated_at = clock_timestamp(),
                        version = version + 1
                    where id = ? and organization_id = ?
                    """, failure.failureCode().name(),
                    truncate(failure.getMessage()), task.id(), organizationId);
                log.warn(
                    "semantic_evaluation_failed complianceRunId={} requirementId={} "
                        + "failureCode={} retryAttempt={}",
                    jobId, task.requirementId(), failure.failureCode(), failure.retryAttempt());
            } catch (RuntimeException failure) {
                failed++;
                jdbc.update("""
                    update requirement_matching_task
                    set status = 'FAILED', error_code = ?, error_message = ?,
                        completed_at = clock_timestamp(), updated_at = clock_timestamp(),
                        version = version + 1
                    where id = ? and organization_id = ?
                    """, failure.getClass().getSimpleName(),
                    truncate(failure.getMessage()), task.id(), organizationId);
            }
            processed++;
            int progress = job.totalRequirementCount() == 0 ? 100
                : (int) Math.round(processed * 100d / job.totalRequirementCount());
            jdbc.update("""
                update compliance_analysis_job
                set processed_requirement_count = ?, completed_count = ?,
                    manual_review_count = ?, failed_count = ?, updated_at = now(),
                    version = version + 1
                where id = ? and organization_id = ?
                """, processed, completed, reviews, failed, jobId, organizationId);
            jobs.event(organizationId, jobId, "PROGRESS", progress,
                "Compliance analysis progressed",
                Map.of("processed", processed, "completed", completed,
                    "manualReview", reviews, "failed", failed));
            outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                "ComplianceAnalysisProgress", "compliance.analysis.progress.v1",
                Map.of("jobId", jobId, "progress", progress,
                    "processed", processed, "completed", completed,
                    "manualReview", reviews, "failed", failed), correlationId);
        }
        String terminal = completed == 0 && failed > 0 ? "FAILED" : "COMPLETED";
        jdbc.update("""
            update compliance_analysis_job
            set status = ?, completed_at = now(), updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, terminal, jobId, organizationId);
        jobs.event(organizationId, jobId, terminal, 100,
            "Compliance analysis finished",
            Map.of("completed", completed, "manualReview", reviews, "failed", failed));
        outbox.publish(organizationId, "ComplianceAnalysis", jobId,
            "COMPLETED".equals(terminal)
                ? "ComplianceAnalysisCompleted" : "ComplianceAnalysisFailed",
            "COMPLETED".equals(terminal)
                ? "compliance.analysis.completed.v1" : "compliance.analysis.failed.v1",
            Map.of("jobId", jobId, "completed", completed,
                "manualReview", reviews, "failed", failed), correlationId);
    }

    private TaskResult evaluate(UUID organizationId, Job job, Task task,
                                UUID correlationId) {
        jdbc.update("""
            update requirement_matching_task
            set status = 'RUNNING', started_at = now(), updated_at = now(),
                version = version + 1
            where id = ? and organization_id = ?
            """, task.id(), organizationId);
        PolicyVersion retrievalPolicy = retrievalPolicy(
            organizationId, job.retrievalPolicyVersionId());
        Snapshot snapshot = snapshot(organizationId, job.snapshotId());
        var retrievalTimer = metrics.retrievalStarted();
        List<CandidateEvidence> candidates;
        try {
            candidates = retriever.retrieve(
                organizationId, task.requirementId(), task.targetEntityId(),
                snapshot.entityCutoff(), snapshot.evidenceCutoff(), retrievalPolicy);
        } finally {
            metrics.retrievalCompleted(retrievalTimer);
        }
        metrics.retrievalCandidates(candidates.size());
        Double topScore = candidates.stream()
            .map(CandidateEvidence::lexicalScore)
            .max(Double::compareTo)
            .orElse(null);
        Map<String, Object> retrievalTrace = new LinkedHashMap<>();
        retrievalTrace.put("complianceRunId", job.id());
        retrievalTrace.put("requirementId", task.requirementId());
        retrievalTrace.put("organizationId", organizationId);
        retrievalTrace.put("projectId", job.projectId());
        retrievalTrace.put("targetEntityId", task.targetEntityId());
        retrievalTrace.put("vectorCollection", null);
        retrievalTrace.put("queryEmbeddingDimension", null);
        retrievalTrace.put("keywordCandidateCount", candidates.size());
        retrievalTrace.put("finalCandidateCount", candidates.size());
        retrievalTrace.put("topScore", topScore);
        retrievalTrace.put("documentScopeLocked", false);
        try {
            log.info("evidence_retrieval {}", mapper.writeValueAsString(retrievalTrace));
        } catch (JsonProcessingException ignored) {
            log.info("evidence_retrieval {}", retrievalTrace);
        }
        var rerankingTimer = metrics.rerankingStarted();
        RankedEvidenceResult ranked;
        try {
            ranked = reranker.rerank(
                new EvidenceRerankingContext(organizationId, task.requirementId(),
                    task.targetEntityId(), candidates, Map.of()), retrievalPolicy);
        } finally {
            metrics.rerankingCompleted(rerankingTimer);
        }
        // Persist retrieval counts before LLM comparison so timeouts do not hide
        // successful candidate discovery.
        jdbc.update("""
            update requirement_matching_task
            set candidate_count = ?, reranked_candidate_count = ?, updated_at = now(),
                version = version + 1
            where id = ? and organization_id = ?
            """, candidates.size(), ranked.ranked().size(), task.id(), organizationId);
        Requirement requirement = requirement(organizationId, task.requirementId());
        EvaluationOutcome outcome;
        if (ranked.ranked().isEmpty()) {
            outcome = missingOutcome(organizationId, job, requirement);
            metrics.complianceMissingEvidence();
        } else {
            String provider = comparisonProvider(organizationId, requirement.attributes());
            if (provider == null || "manual-only".equals(provider)) {
                outcome = semanticOutcome(
                    organizationId, job, requirement, ranked, correlationId);
                metrics.complianceLlm();
            } else {
                outcome = deterministicOutcome(
                    organizationId, job, requirement, ranked, provider);
                metrics.complianceDeterministic();
                metrics.comparisonStrategy(provider);
            }
        }
        UUID evaluationId = persistEvaluation(organizationId, job, task, requirement,
            ranked, outcome);
        jdbc.update("""
            update requirement_matching_task
            set status = 'COMPLETED', candidate_count = ?,
                reranked_candidate_count = ?, selected_evidence_count = ?,
                evaluation_count = 1, model_run_id = ?, completed_at = now(),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, candidates.size(), ranked.ranked().size(), outcome.selectedEvidence().size(),
            outcome.modelRunId(), task.id(), organizationId);
        if (outcome.requiresReview()) {
            metrics.complianceManualReview();
            outbox.publish(organizationId, "ComplianceEvaluation", evaluationId,
                "ComplianceReviewRequired", "compliance.review.required.v1",
                Map.of("evaluationId", evaluationId,
                    "requirementId", task.requirementId()), correlationId);
        }
        if (outcome.contradiction()) {
            metrics.complianceContradictoryEvidence();
        }
        metrics.complianceEvaluation();
        return new TaskResult(outcome.requiresReview());
    }

    private EvaluationOutcome deterministicOutcome(
        UUID organizationId, Job job, Requirement requirement,
        RankedEvidenceResult ranked, String provider
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
        String outcome = switch (comparison.status()) {
            case "SATISFIED" -> "SATISFIED";
            case "NOT_SATISFIED" -> "NOT_SATISFIED";
            default -> "INDETERMINATE";
        };
        UUID decision = decisionByOutcome(organizationId, outcome);
        JsonNode confidencePolicy = policyConfiguration(
            organizationId, job.confidencePolicyVersionId());
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
        return new EvaluationOutcome(decision, summary, confidence.score(),
            confidence.requiresReview(), null, List.of(best), contradiction, null);
    }

    private EvaluationOutcome semanticOutcome(
        UUID organizationId, Job job, Requirement requirement,
        RankedEvidenceResult ranked, UUID correlationId
    ) {
        Prompt prompt = prompt(organizationId, job.promptPackageVersionId());
        List<Map<String, Object>> evidence = selectedEvidencePayload(
            organizationId, ranked.ranked());
        List<Decision> decisions = decisions(organizationId);
        boolean contradiction = evidence.stream().anyMatch(item ->
            number(item.get("contradictionStrength")) > 0);
        SemanticRequest request = new SemanticRequest(
            organizationId, "nanobase-spec-ai", modelProfile(organizationId),
            prompt.components(), prompt.schema(),
            Map.of("id", requirement.id(), "text", requirement.text(),
                "attributes", requirement.attributes()),
            ontology(organizationId, job.analysisProfileId()), evidence,
            decisions.stream().map(Decision::code).toList(), maxOutputTokens, correlationId);
        ComplianceSemanticRouter.RoutedEvaluation routed =
            semanticRouter.evaluate(request, contradiction);
        var response = routed.response();
        String decisionCode = response.output()
            .path("recommendedDecisionConcept").asText();
        if (decisionCode == null || decisionCode.isBlank()) {
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Semantic evaluator returned an empty decision concept", 0);
        }
        UUID decisionId = decisions.stream()
            .filter(item -> item.code().equals(decisionCode))
            .map(Decision::id).findFirst()
            .orElseThrow(() -> new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Semantic evaluator returned an unsupported decision concept: " + decisionCode,
                0));
        Set<String> allowed = evidence.stream()
            .map(item -> String.valueOf(item.get("id")))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowedDecisions = decisions.stream()
            .map(Decision::code)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        StructuredComplianceResponseValidator.ValidationResult validation =
            new StructuredComplianceResponseValidator()
                .validate(response.output(), allowed, allowedDecisions);
        if (!validation.valid()) {
            throw new SemanticEvaluationException(
                validation.failureCode() == null
                    ? SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE
                    : validation.failureCode(),
                validation.message(), 0);
        }
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of("relevance", averageScore(ranked.ranked()),
                "validity", averageValidity(ranked.ranked()),
                "authority", averageAuthority(ranked.ranked()),
                "grounding", 1d, "deterministic", 0d,
                "entityResolution", 0.8, "freshness", 0.7,
                "historicalAcceptance", 0.5),
            false, contradiction,
            policyConfiguration(organizationId, job.confidencePolicyVersionId())));
        double modelConfidence = response.output().path("confidence").asDouble(0);
        double combined = (confidence.score() + modelConfidence) / 2d;
        boolean review = response.output().path("requiresManualReview").asBoolean()
            || confidence.requiresReview() || contradiction;
        ObjectNode summary = mapper.createObjectNode();
        summary.put("strategyProvider", "llm-semantic-evaluation");
        summary.set("semanticEvaluation", response.output());
        summary.set("confidence", mapper.valueToTree(confidence));
        summary.set("modelRouting", mapper.valueToTree(routed.routing()));
        return new EvaluationOutcome(decisionId, summary, combined, review,
            response.modelRunId(), ranked.ranked(), contradiction, routed.routing());
    }

    private EvaluationOutcome missingOutcome(UUID organizationId, Job job,
                                             Requirement requirement) {
        UUID decision = decisionByOutcome(organizationId, "INDETERMINATE");
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of(), true, false,
            policyConfiguration(organizationId, job.confidencePolicyVersionId())));
        ObjectNode summary = mapper.createObjectNode();
        summary.put("status", "MISSING_EVIDENCE");
        summary.put("requirementId", requirement.id().toString());
        summary.set("confidence", mapper.valueToTree(confidence));
        return new EvaluationOutcome(decision, summary, confidence.score(),
            true, null, List.of(), false, null);
    }

    private UUID persistEvaluation(
        UUID organizationId, Job job, Task task, Requirement requirement,
        RankedEvidenceResult ranked, EvaluationOutcome outcome
    ) {
        UUID evaluationId = UUID.randomUUID();
        UUID grounding = conceptByMetadata(organizationId, "grounded",
            !outcome.selectedEvidence().isEmpty());
        ComplianceSemanticRouter.RoutingTrace routing = outcome.routing();
        jdbc.update("""
            insert into compliance_evaluation (
                id, organization_id, project_id, requirement_id, target_scope_json,
                analysis_job_id, knowledge_snapshot_id, suggested_decision_concept_id,
                comparison_summary_json, combined_confidence,
                grounding_status_concept_id, review_status, analysis_profile_id,
                retrieval_policy_version_id, matching_policy_version_id,
                comparison_policy_version_id, confidence_policy_version_id,
                prompt_package_version_id,
                live_model_profile, shadow_model_profile, shadow_result_json,
                shadow_comparison_json, escalation_reason,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?::jsonb, ?::jsonb, ?,
                      now(), now())
            """, evaluationId, organizationId, job.projectId(), requirement.id(),
            task.targetEntityId() == null ? "{}"
                : json(Map.of("targetEntityId", task.targetEntityId())),
            job.id(), job.snapshotId(), outcome.decisionConceptId(),
            outcome.summary().toString(),
            outcome.confidence(), grounding,
            outcome.requiresReview() ? "REQUIRES_REVIEW" : "AI_RECOMMENDATION",
            job.analysisProfileId(), job.retrievalPolicyVersionId(),
            job.matchingPolicyVersionId(), job.comparisonPolicyVersionId(),
            job.confidencePolicyVersionId(), job.promptPackageVersionId(),
            routing == null ? null : routing.liveProfile(),
            routing == null ? null : routing.shadowProfile(),
            routing == null || routing.shadowResult() == null
                ? null : routing.shadowResult().toString(),
            routing == null || routing.comparison() == null
                ? null : routing.comparison().toString(),
            routing == null ? null : routing.escalationReason());
        for (RankedEvidence selected : outcome.selectedEvidence()) {
            boolean contradiction = outcome.contradiction();
            UUID role = conceptByMetadata(organizationId, "polarity",
                contradiction ? -1 : 1);
            jdbc.update("""
                insert into compliance_evidence_link (
                    id, organization_id, compliance_evaluation_id,
                    evidence_fragment_id, evidence_claim_id, relation_role_concept_id,
                    relevance_score, validity_score, support_strength,
                    contradiction_strength, selected_by_type, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), organizationId, evaluationId,
                selected.evidence().fragmentId(), selected.evidence().claimId(), role,
                selected.score(), selected.evidence().validityScore(),
                contradiction ? 0 : selected.score(),
                contradiction ? selected.score() : 0,
                outcome.modelRunId() == null ? "DETERMINISTIC" : "MODEL");
        }
        return evaluationId;
    }

    private List<Map<String, Object>> selectedEvidencePayload(
        UUID organizationId, List<RankedEvidence> ranked
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RankedEvidence item : ranked) {
            Map<String, Object> row = jdbc.queryForMap("""
                select id, fragment_text as text, page_number,
                       valid_from, valid_until
                from evidence_fragment where id = ? and organization_id = ?
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

    private Job job(UUID organizationId, UUID id) {
        return jdbc.query("""
            select job.*, snapshot.entity_version_cutoff,
                   snapshot.evidence_version_cutoff
            from compliance_analysis_job job
            join knowledge_snapshot snapshot on snapshot.id = job.knowledge_snapshot_id
             and snapshot.organization_id = job.organization_id
            where job.id = ? and job.organization_id = ? for update
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Compliance analysis job not found");
                }
                return new Job(result.getObject("id", UUID.class),
                    result.getObject("project_id", UUID.class), result.getString("status"),
                    result.getObject("analysis_profile_id", UUID.class),
                    result.getObject("knowledge_snapshot_id", UUID.class),
                    result.getObject("retrieval_policy_version_id", UUID.class),
                    result.getObject("matching_policy_version_id", UUID.class),
                    result.getObject("comparison_policy_version_id", UUID.class),
                    result.getObject("confidence_policy_version_id", UUID.class),
                    result.getObject("prompt_package_version_id", UUID.class),
                    result.getInt("total_requirement_count"));
            }, id, organizationId);
    }

    private List<Task> tasks(UUID organizationId, UUID jobId) {
        return jdbc.query("""
            select task.id, task.requirement_id,
                   (requirement.attributes_json ->> 'targetEntityId')::uuid target_entity_id
            from requirement_matching_task task
            join requirement on requirement.id = task.requirement_id
             and requirement.organization_id = task.organization_id
            where task.organization_id = ? and task.compliance_job_id = ?
              and task.status = 'QUEUED'
            order by task.created_at, task.id
            """, (result, row) -> new Task(result.getObject(1, UUID.class),
                result.getObject(2, UUID.class), result.getObject(3, UUID.class)),
            organizationId, jobId);
    }

    private Snapshot snapshot(UUID organizationId, UUID id) {
        return jdbc.query("""
            select entity_version_cutoff, evidence_version_cutoff
            from knowledge_snapshot where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Knowledge snapshot is missing");
                }
                return new Snapshot(result.getTimestamp(1).toInstant(),
                    result.getTimestamp(2).toInstant());
            }, id, organizationId);
    }

    private Requirement requirement(UUID organizationId, UUID id) {
        return jdbc.query("""
            select id, requirement_code, requirement_text, attributes_json::text
            from requirement where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Requirement not found");
                }
                return new Requirement(result.getObject(1, UUID.class),
                    result.getString(2), result.getString(3), tree(result.getString(4)));
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
            select configuration_json::text from policy_version
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
            throw new IllegalStateException("Decision outcome is not configured: " + outcome);
        }
        return ids.getFirst();
    }

    private UUID conceptByMetadata(UUID organizationId, String key, Object value) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where active = true and metadata_json ->> ? = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            key, String.valueOf(value), organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Required ontology metadata is not configured");
        }
        return ids.getFirst();
    }

    private List<Decision> decisions(UUID organizationId) {
        return jdbc.query("""
            select id, concept_code from ontology_concept
            where concept_type = 'DECISION' and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order
            """, (result, row) -> new Decision(result.getObject(1, UUID.class),
                result.getString(2)), organizationId);
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

    private List<Map<String, Object>> ontology(UUID organizationId,
                                               UUID analysisProfileId) {
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

    private double averageScore(List<RankedEvidence> values) {
        return values.stream().mapToDouble(RankedEvidence::score).average().orElse(0);
    }

    private double averageValidity(List<RankedEvidence> values) {
        return values.stream().mapToDouble(value -> value.evidence().validityScore())
            .average().orElse(0);
    }

    private double averageAuthority(List<RankedEvidence> values) {
        return values.stream().mapToDouble(value -> value.evidence().authorityScore())
            .average().orElse(0);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
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

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Compliance value is not serializable", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "Compliance task failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record Job(
        UUID id,
        UUID projectId,
        String status,
        UUID analysisProfileId,
        UUID snapshotId,
        UUID retrievalPolicyVersionId,
        UUID matchingPolicyVersionId,
        UUID comparisonPolicyVersionId,
        UUID confidencePolicyVersionId,
        UUID promptPackageVersionId,
        int totalRequirementCount
    ) {
    }

    private record Task(UUID id, UUID requirementId, UUID targetEntityId) {
    }

    private record Snapshot(Instant entityCutoff, Instant evidenceCutoff) {
    }

    private record Requirement(UUID id, String code, String text, JsonNode attributes) {
    }

    private record Decision(UUID id, String code) {
    }

    private record Prompt(List<String> components, JsonNode schema) {
    }

    private record EvaluationOutcome(
        UUID decisionConceptId,
        JsonNode summary,
        double confidence,
        boolean requiresReview,
        UUID modelRunId,
        List<RankedEvidence> selectedEvidence,
        boolean contradiction,
        ComplianceSemanticRouter.RoutingTrace routing
    ) {
    }

    private record TaskResult(boolean requiresReview) {
    }
}
