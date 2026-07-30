package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceResult;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * Transaction-free model execution. Must never open or join a database transaction
 * and must not access repositories / EntityManager.
 */
@Service
public class ComplianceTaskModelExecutionService {
    private static final Logger log =
        LoggerFactory.getLogger(ComplianceTaskModelExecutionService.class);

    private final ComplianceSemanticRouter semanticRouter;
    private final ComplianceDecisionSafetyGuard decisionSafetyGuard;
    private final PolicyComplianceConfidenceEngine confidenceEngine;
    private final ObjectMapper mapper;
    private final PlatformMetrics metrics;
    private final DataSource dataSource;

    public ComplianceTaskModelExecutionService(ComplianceSemanticRouter semanticRouter,
                                               ObjectMapper mapper,
                                               PolicyComplianceConfidenceEngine confidenceEngine,
                                               PlatformMetrics metrics,
                                               DataSource dataSource) {
        this.semanticRouter = semanticRouter;
        this.mapper = mapper;
        this.decisionSafetyGuard = new ComplianceDecisionSafetyGuard(mapper);
        this.confidenceEngine = confidenceEngine;
        this.metrics = metrics;
        this.dataSource = dataSource;
    }

    @Transactional(propagation = Propagation.NEVER)
    public ComplianceExecutionResult execute(PreparedComplianceTask prepared) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            metrics.complianceConnectionHeldDuringModel();
            throw new IllegalStateException(
                "Compliance model execution must not run inside a transaction");
        }
        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            metrics.complianceConnectionHeldDuringModel();
            throw new IllegalStateException(
                "Compliance model execution must not hold a pooled database connection");
        }
        if (!prepared.needsModelExecution()) {
            return ComplianceExecutionResult.fromPrecomputed(prepared.precomputedOutcome());
        }
        log.info("event=COMPLIANCE_TASK_EXECUTE_STARTED jobId={} taskId={} leaseGeneration={}",
            prepared.jobId(), prepared.taskId(), prepared.leaseGeneration());
        long started = System.nanoTime();
        try {
            log.info("event=COMPLIANCE_MODEL_REQUEST_STARTED jobId={} requirementId={} "
                    + "correlationId={}",
                prepared.jobId(), prepared.requirementId(), prepared.correlationId());
            ComplianceSemanticRouter.RoutedEvaluation routed =
                semanticRouter.evaluate(prepared.semanticRequest(), prepared.contradictionHint());
            log.info("event=COMPLIANCE_MODEL_REQUEST_COMPLETED jobId={} requirementId={} "
                    + "correlationId={}",
                prepared.jobId(), prepared.requirementId(), prepared.correlationId());
            var response = routed.response();
            List<Map<String, Object>> evidence = prepared.evidencePayload();
            ObjectNode safeOutput = decisionSafetyGuard.normalize(
                response.output(), prepared.requirement().text(), evidence);
            String rawDecision = safeOutput.path("recommendedDecisionConcept").asText();
            if (rawDecision == null || rawDecision.isBlank()) {
                rawDecision = safeOutput.path("decision").asText();
            }
            if (rawDecision == null || rawDecision.isBlank()) {
                return ComplianceExecutionResult.failure(
                    SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE.name(),
                    "Semantic evaluator returned an empty decision concept");
            }
            final String decisionCode = rawDecision;
            UUID decisionId = prepared.decisions().stream()
                .filter(item -> item.code().equals(decisionCode))
                .map(PreparedComplianceTask.DecisionSnapshot::id)
                .findFirst()
                .orElse(null);
            if (decisionId == null) {
                return ComplianceExecutionResult.failure(
                    SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE.name(),
                    "Semantic evaluator returned an unsupported decision concept: " + decisionCode);
            }
            Set<String> allowed = evidence.stream()
                .map(item -> String.valueOf(item.get("id")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> allowedDecisions = prepared.decisions().stream()
                .map(PreparedComplianceTask.DecisionSnapshot::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            StructuredComplianceResponseValidator.ValidationResult validation =
                new StructuredComplianceResponseValidator()
                    .validate(safeOutput, allowed, allowedDecisions);
            if (!validation.valid()) {
                return ComplianceExecutionResult.failure(
                    validation.failureCode() == null
                        ? SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE.name()
                        : validation.failureCode().name(),
                    validation.message());
            }
            boolean contradiction = prepared.contradictionHint();
            ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
                Map.of("relevance", averageScore(prepared),
                    "validity", averageValidity(prepared),
                    "authority", averageAuthority(prepared),
                    "grounding", 1d, "deterministic", 0d,
                    "entityResolution", 0.8, "freshness", 0.7,
                    "historicalAcceptance", 0.5),
                false, contradiction, prepared.confidencePolicy()));
            double modelConfidence = safeOutput.path("confidence").asDouble(0);
            double combined = (confidence.score() + modelConfidence) / 2d;
            boolean review = safeOutput.path("requiresManualReview").asBoolean()
                || confidence.requiresReview() || contradiction
                || "NON_COMPLIANT".equals(decisionCode)
                || "INSUFFICIENT_INFORMATION".equals(decisionCode);
            ObjectNode summary = mapper.createObjectNode();
            summary.put("strategyProvider", "llm-semantic-evaluation");
            summary.put("resultLabel", "AI Ön Değerlendirmesi");
            summary.set("semanticEvaluation", safeOutput);
            summary.set("confidence", mapper.valueToTree(confidence));
            summary.set("modelRouting", mapper.valueToTree(routed.routing()));
            metrics.complianceExecuteDuration(
                java.time.Duration.ofNanos(System.nanoTime() - started));
            log.info("event=COMPLIANCE_TASK_EXECUTE_COMPLETED jobId={} taskId={}",
                prepared.jobId(), prepared.taskId());
            return new ComplianceExecutionResult(
                true, false, false, null, null,
                decisionId, decisionCode, summary, combined, review,
                response.modelRunId(), prepared.ranked().ranked(), contradiction,
                routed.routing(), "LLM", List.of(), false, null);
        } catch (SemanticEvaluationException failure) {
            metrics.complianceExecuteDuration(
                java.time.Duration.ofNanos(System.nanoTime() - started));
            return ComplianceExecutionResult.failure(
                domainErrorCode(failure.failureCode()), failure.getMessage());
        } catch (RuntimeException failure) {
            metrics.complianceExecuteDuration(
                java.time.Duration.ofNanos(System.nanoTime() - started));
            String code = mapTimeoutCode(failure);
            return ComplianceExecutionResult.failure(code, failure.getMessage());
        }
    }

    private static String domainErrorCode(SemanticEvaluationFailureCode code) {
        return switch (code) {
            case LLM_TIMEOUT, LLM_GENERATION_TIMEOUT -> "MODEL_TIMEOUT";
            case LLM_UNAVAILABLE, LLM_CONNECT_TIMEOUT -> "MODEL_UNAVAILABLE";
            case LLM_OVERLOADED, LLM_QUEUE_TIMEOUT -> "SLOT_WAIT_TIMEOUT";
            case CAPACITY_FULL -> "CAPACITY_FULL";
            case CAPACITY_WAIT_TIMEOUT -> "CAPACITY_WAIT_TIMEOUT";
            case CAPACITY_LEASE_LOST -> "CAPACITY_LEASE_LOST";
            case CAPACITY_PROVIDER_UNAVAILABLE -> "CAPACITY_PROVIDER_UNAVAILABLE";
            case LLM_CANCELLED -> "CANCEL_REQUESTED";
            default -> code.name();
        };
    }

    private static String mapTimeoutCode(RuntimeException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toUpperCase();
        if (message.contains("TIMEOUT") || failure.getClass().getSimpleName().contains("Timeout")) {
            return "MODEL_TIMEOUT";
        }
        if (message.contains("UNAVAILABLE") || message.contains("CONNECTION")) {
            return SemanticEvaluationFailureCode.LLM_UNAVAILABLE.name();
        }
        return "EVALUATION_ERROR";
    }

    private double averageScore(PreparedComplianceTask prepared) {
        return prepared.ranked().ranked().stream()
            .mapToDouble(com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence::score)
            .average().orElse(0);
    }

    private double averageValidity(PreparedComplianceTask prepared) {
        return prepared.ranked().ranked().stream()
            .mapToDouble(value -> value.evidence().validityScore()).average().orElse(0);
    }

    private double averageAuthority(PreparedComplianceTask prepared) {
        return prepared.ranked().ranked().stream()
            .mapToDouble(value -> value.evidence().authorityScore()).average().orElse(0);
    }
}
