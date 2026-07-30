package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticRequest;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticResponse;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Routes compliance semantic evaluation across FAST and BALANCED profiles.
 *
 * <p>SHADOW never changes the live decision. LIVE_FAST uses FAST when safe and escalates
 * to the live/BALANCED profile on policy triggers or FAST technical failure.
 */
@Component
public class ComplianceSemanticRouter {
    private static final Logger log = LoggerFactory.getLogger(ComplianceSemanticRouter.class);

    private final ComplianceAiGateway aiGateway;
    private final ObjectMapper mapper;
    private final PlatformMetrics metrics;
    private final ComplianceRoutingMode mode;
    private final String liveProfile;
    private final String fastProfile;
    private final int liveMaxOutputTokens;
    private final int fastMaxOutputTokens;
    private final boolean fastEnabled;
    private final boolean shadowEnabled;
    private final ComplianceEscalationPolicy escalationPolicy;

    public ComplianceSemanticRouter(
        ComplianceAiGateway aiGateway,
        ObjectMapper mapper,
        PlatformMetrics metrics,
        @Value("${specai.compliance.routing.mode:BALANCED_ONLY}") String mode,
        @Value("${specai.compliance.routing.live-profile:BALANCED}") String liveProfile,
        @Value("${specai.compliance.routing.fast-profile:FAST}") String fastProfile,
        @Value("${specai.ai-orchestrator.compliance-max-output-tokens:1024}")
            int liveMaxOutputTokens,
        @Value("${specai.compliance.routing.fast-max-output-tokens:512}")
            int fastMaxOutputTokens,
        @Value("${specai.compliance.routing.escalate-confidence-below:0.70}")
            double escalateConfidenceBelow,
        @Value("${specai.compliance.routing.escalate-evidence-above:2}")
            int escalateEvidenceAbove,
        @Value("${specai.compliance.routing.fast-enabled:false}") boolean fastEnabled,
        @Value("${specai.compliance.routing.shadow-enabled:false}") boolean shadowEnabled
    ) {
        this.aiGateway = aiGateway;
        this.mapper = mapper;
        this.metrics = metrics;
        this.fastEnabled = fastEnabled;
        this.shadowEnabled = shadowEnabled;
        this.mode = resolveMode(mode, fastEnabled, shadowEnabled);
        this.liveProfile = blankToDefault(liveProfile, "BALANCED");
        this.fastProfile = blankToDefault(fastProfile, "FAST");
        this.liveMaxOutputTokens = liveMaxOutputTokens > 0 ? liveMaxOutputTokens : 1024;
        this.fastMaxOutputTokens = fastMaxOutputTokens > 0 ? fastMaxOutputTokens : 512;
        this.escalationPolicy = new ComplianceEscalationPolicy(
            escalateConfidenceBelow, escalateEvidenceAbove);
        log.info(
            "compliance_routing_policy mode={} requestedMode={} liveProfile={} fastProfile={} "
                + "fastEnabled={} shadowEnabled={} liveMaxTokens={} fastMaxTokens={} "
                + "escalateConfidenceBelow={} escalateEvidenceAbove={} "
                + "deploymentAlias=nanobase-balanced",
            this.mode, blankToDefault(mode, "BALANCED_ONLY"), this.liveProfile, this.fastProfile,
            this.fastEnabled, this.shadowEnabled,
            this.liveMaxOutputTokens, this.fastMaxOutputTokens,
            this.escalationPolicy.confidenceBelow(), this.escalationPolicy.evidenceAbove());
    }

    /**
     * Production V1 keeps FAST/SHADOW code paths but forces BALANCED_ONLY unless both the
     * routing mode and the corresponding feature flag explicitly enable them.
     */
    static ComplianceRoutingMode resolveMode(String rawMode, boolean fastEnabled,
                                             boolean shadowEnabled) {
        ComplianceRoutingMode requested = ComplianceRoutingMode.from(rawMode);
        return switch (requested) {
            case BALANCED_ONLY -> ComplianceRoutingMode.BALANCED_ONLY;
            case SHADOW -> shadowEnabled
                ? ComplianceRoutingMode.SHADOW : ComplianceRoutingMode.BALANCED_ONLY;
            case LIVE_FAST -> fastEnabled
                ? ComplianceRoutingMode.LIVE_FAST : ComplianceRoutingMode.BALANCED_ONLY;
        };
    }

    public ComplianceRoutingMode mode() {
        return mode;
    }

    public RoutedEvaluation evaluate(SemanticRequest template, boolean contradiction) {
        int evidenceCount = template.evidence() == null ? 0 : template.evidence().size();
        return switch (mode) {
            case BALANCED_ONLY -> balancedOnly(template);
            case SHADOW -> shadow(template, evidenceCount, contradiction);
            case LIVE_FAST -> liveFast(template, evidenceCount, contradiction);
        };
    }

    private RoutedEvaluation balancedOnly(SemanticRequest template) {
        TimedCall live = call(withProfile(template, liveProfile, liveMaxOutputTokens));
        metrics.complianceLlmProfile(liveProfile, live.success(), live.duration());
        if (!live.success()) {
            throw live.failure();
        }
        return new RoutedEvaluation(live.response(), routingTrace(
            liveProfile, null, null, null, null, live.duration(), null));
    }

    private RoutedEvaluation shadow(SemanticRequest template, int evidenceCount,
                                    boolean contradiction) {
        TimedCall shadow = safeCall(withProfile(template, fastProfile, fastMaxOutputTokens));
        metrics.complianceLlmProfile(fastProfile, shadow.success(), shadow.duration());
        metrics.complianceShadowAttempt();

        TimedCall live = call(withProfile(template, liveProfile, liveMaxOutputTokens));
        metrics.complianceLlmProfile(liveProfile, live.success(), live.duration());
        if (!live.success()) {
            throw live.failure();
        }

        ObjectNode comparison = mapper.createObjectNode();
        comparison.put("mode", ComplianceRoutingMode.SHADOW.name());
        comparison.put("evidenceCount", evidenceCount);
        comparison.put("contradiction", contradiction);
        String wouldEscalate = null;
        if (shadow.success()) {
            wouldEscalate = escalationPolicy.reason(
                shadow.response().output(), evidenceCount, contradiction);
            String shadowDecision = decisionOf(shadow.response());
            String liveDecision = decisionOf(live.response());
            boolean agreement = shadowDecision != null && shadowDecision.equals(liveDecision);
            comparison.put("agreement", agreement);
            comparison.put("shadowDecision", shadowDecision);
            comparison.put("liveDecision", liveDecision);
            comparison.put("shadowConfidence",
                shadow.response().output().path("confidence").asDouble(0));
            comparison.put("liveConfidence",
                live.response().output().path("confidence").asDouble(0));
            comparison.put("shadowLatencyMs", shadow.response().latencyMs());
            comparison.put("liveLatencyMs", live.response().latencyMs());
            comparison.put("wouldEscalate", wouldEscalate != null);
            if (wouldEscalate != null) {
                comparison.put("wouldEscalateReason", wouldEscalate);
            }
            metrics.complianceShadowAgreement(agreement);
            if ("COMPLIANT".equals(shadowDecision) && !"COMPLIANT".equals(liveDecision)) {
                metrics.complianceFalseCompliant();
            }
            if (!agreement) {
                metrics.sprint9("shadow_disagreement_rate");
            }
        } else {
            comparison.put("agreement", false);
            comparison.put("shadowFailed", true);
            comparison.put("shadowFailureCode", shadow.failure().failureCode().name());
            comparison.put("shadowFailureMessage", truncate(shadow.failure().getMessage()));
            metrics.complianceShadowFailure(shadow.failure().failureCode().name());
            if (shadow.failure().failureCode()
                == SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE) {
                metrics.complianceStructuredJsonFailure(fastProfile);
            }
        }
        log.info("compliance_shadow_comparison {}", comparison);
        return new RoutedEvaluation(live.response(), routingTrace(
            liveProfile, fastProfile,
            shadow.success() ? shadow.response().output() : null,
            comparison, null, live.duration(), shadow.duration()));
    }

    private RoutedEvaluation liveFast(SemanticRequest template, int evidenceCount,
                                      boolean contradiction) {
        TimedCall fast = safeCall(withProfile(template, fastProfile, fastMaxOutputTokens));
        metrics.complianceLlmProfile(fastProfile, fast.success(), fast.duration());
        if (fast.success()) {
            String reason = escalationPolicy.reason(
                fast.response().output(), evidenceCount, contradiction);
            if (reason == null) {
                ObjectNode comparison = mapper.createObjectNode();
                comparison.put("mode", ComplianceRoutingMode.LIVE_FAST.name());
                comparison.put("escalated", false);
                comparison.put("liveDecision", decisionOf(fast.response()));
                comparison.put("fastLatencyMs", fast.response().latencyMs());
                return new RoutedEvaluation(fast.response(), routingTrace(
                    fastProfile, null, null, comparison, null, fast.duration(), null));
            }
            metrics.complianceFastEscalation(reason);
            TimedCall live = call(withProfile(template, liveProfile, liveMaxOutputTokens));
            metrics.complianceLlmProfile(liveProfile, live.success(), live.duration());
            if (!live.success()) {
                throw live.failure();
            }
            ObjectNode comparison = mapper.createObjectNode();
            comparison.put("mode", ComplianceRoutingMode.LIVE_FAST.name());
            comparison.put("escalated", true);
            comparison.put("escalationReason", reason);
            comparison.put("fastDecision", decisionOf(fast.response()));
            comparison.put("liveDecision", decisionOf(live.response()));
            comparison.put("agreement",
                decisionOf(fast.response()) != null
                    && decisionOf(fast.response()).equals(decisionOf(live.response())));
            comparison.put("fastLatencyMs", fast.response().latencyMs());
            comparison.put("liveLatencyMs", live.response().latencyMs());
            if ("COMPLIANT".equals(decisionOf(fast.response()))
                && !"COMPLIANT".equals(decisionOf(live.response()))) {
                metrics.complianceFalseCompliant();
            }
            return new RoutedEvaluation(live.response(), routingTrace(
                liveProfile, fastProfile, fast.response().output(), comparison, reason,
                live.duration(), fast.duration()));
        }
        metrics.complianceFastEscalation("FAST_FAILURE");
        if (fast.failure().failureCode()
            == SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE) {
            metrics.complianceStructuredJsonFailure(fastProfile);
        }
        TimedCall live = call(withProfile(template, liveProfile, liveMaxOutputTokens));
        metrics.complianceLlmProfile(liveProfile, live.success(), live.duration());
        if (!live.success()) {
            throw live.failure();
        }
        ObjectNode comparison = mapper.createObjectNode();
        comparison.put("mode", ComplianceRoutingMode.LIVE_FAST.name());
        comparison.put("escalated", true);
        comparison.put("escalationReason", "FAST_FAILURE");
        comparison.put("fastFailureCode", fast.failure().failureCode().name());
        comparison.put("liveDecision", decisionOf(live.response()));
        return new RoutedEvaluation(live.response(), routingTrace(
            liveProfile, fastProfile, null, comparison, "FAST_FAILURE",
            live.duration(), fast.duration()));
    }

    private TimedCall call(SemanticRequest request) {
        long started = System.nanoTime();
        try {
            SemanticResponse response = aiGateway.evaluate(request);
            return TimedCall.ok(response, Duration.ofNanos(System.nanoTime() - started));
        } catch (SemanticEvaluationException failure) {
            return TimedCall.err(failure, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private TimedCall safeCall(SemanticRequest request) {
        return call(request);
    }

    private SemanticRequest withProfile(SemanticRequest template, String profile,
                                        int maxTokens) {
        return new SemanticRequest(
            template.organizationId(),
            template.logicalModel(),
            profile,
            template.promptComponents(),
            template.outputSchema(),
            template.requirement(),
            template.ontologyConcepts(),
            template.evidence(),
            template.allowedDecisionConcepts(),
            maxTokens,
            template.correlationId());
    }

    private RoutingTrace routingTrace(
        String liveProfileCode,
        String shadowProfileCode,
        JsonNode shadowResult,
        JsonNode comparison,
        String escalationReason,
        Duration liveDuration,
        Duration shadowDuration
    ) {
        Map<String, Object> logTrace = new LinkedHashMap<>();
        logTrace.put("mode", mode.name());
        logTrace.put("liveProfile", liveProfileCode);
        logTrace.put("shadowProfile", shadowProfileCode);
        logTrace.put("escalationReason", escalationReason);
        logTrace.put("liveDurationMs", liveDuration == null ? null : liveDuration.toMillis());
        logTrace.put("shadowDurationMs",
            shadowDuration == null ? null : shadowDuration.toMillis());
        log.info("compliance_model_routing {}", logTrace);
        return new RoutingTrace(
            mode.name(), liveProfileCode, shadowProfileCode, shadowResult, comparison,
            escalationReason);
    }

    private static String decisionOf(SemanticResponse response) {
        if (response == null || response.output() == null) {
            return null;
        }
        String code = response.output().path("recommendedDecisionConcept").asText(null);
        return code == null || code.isBlank() ? null : code;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.substring(0, Math.min(300, message.length()));
    }

    public record RoutedEvaluation(SemanticResponse response, RoutingTrace routing) {
    }

    public record RoutingTrace(
        String mode,
        String liveProfile,
        String shadowProfile,
        JsonNode shadowResult,
        JsonNode comparison,
        String escalationReason
    ) {
    }

    private record TimedCall(
        SemanticResponse response,
        SemanticEvaluationException failure,
        Duration duration
    ) {
        static TimedCall ok(SemanticResponse response, Duration duration) {
            return new TimedCall(response, null, duration);
        }

        static TimedCall err(SemanticEvaluationException failure, Duration duration) {
            return new TimedCall(null, failure, duration);
        }

        boolean success() {
            return response != null;
        }
    }
}
