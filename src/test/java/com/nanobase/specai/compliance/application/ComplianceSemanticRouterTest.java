package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticRequest;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticResponse;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComplianceSemanticRouterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ComplianceAiGateway gateway;
    private PlatformMetrics metrics;

    @BeforeEach
    void setUp() {
        gateway = mock(ComplianceAiGateway.class);
        metrics = new PlatformMetrics(new SimpleMeterRegistry(),
            mock(com.nanobase.specai.integration.outbox.OutboxEventRepository.class));
    }

    @Test
    void escalationPolicyTriggersOnLowConfidenceContradictionAndMultiEvidence() {
        ComplianceEscalationPolicy policy = new ComplianceEscalationPolicy(0.70, 2);
        ObjectNode low = mapper.createObjectNode().put("confidence", 0.4)
            .put("requiresManualReview", false);
        assertThat(policy.reason(low, 1, false)).isEqualTo("LOW_CONFIDENCE");
        assertThat(policy.reason(mapper.createObjectNode().put("confidence", 0.9), 1, true))
            .isEqualTo("CONTRADICTION");
        assertThat(policy.reason(mapper.createObjectNode().put("confidence", 0.9), 3, false))
            .isEqualTo("MULTI_EVIDENCE");
        assertThat(policy.reason(mapper.createObjectNode()
            .put("confidence", 0.9).put("requiresManualReview", true), 1, false))
            .isEqualTo("MANUAL_REVIEW_FLAG");
        assertThat(policy.shouldEscalate(mapper.createObjectNode()
            .put("confidence", 0.95).put("requiresManualReview", false), 2, false))
            .isFalse();
    }

    @Test
    void shadowKeepsLiveDecisionAndRecordsDisagreement() {
        ComplianceSemanticRouter router = router("SHADOW");
        when(gateway.evaluate(any())).thenAnswer(invocation -> {
            SemanticRequest request = invocation.getArgument(0);
            String decision = "FAST".equals(request.modelProfile())
                ? "COMPLIANT" : "NON_COMPLIANT";
            return response(decision, 0.9, false);
        });

        var routed = router.evaluate(request("BALANCED"), false);

        assertThat(routed.response().output().path("recommendedDecisionConcept").asText())
            .isEqualTo("NON_COMPLIANT");
        assertThat(routed.routing().liveProfile()).isEqualTo("BALANCED");
        assertThat(routed.routing().shadowProfile()).isEqualTo("FAST");
        assertThat(routed.routing().comparison().path("agreement").asBoolean()).isFalse();
        verify(gateway, times(2)).evaluate(any());
    }

    @Test
    void liveFastEscalatesOnLowConfidence() {
        ComplianceSemanticRouter router = router("LIVE_FAST");
        when(gateway.evaluate(any())).thenAnswer(invocation -> {
            SemanticRequest request = invocation.getArgument(0);
            if ("FAST".equals(request.modelProfile())) {
                assertThat(request.maximumOutputTokens()).isEqualTo(512);
                return response("COMPLIANT", 0.40, false);
            }
            return response("INSUFFICIENT_INFORMATION", 0.85, true);
        });

        var routed = router.evaluate(request("BALANCED"), false);

        assertThat(routed.routing().escalationReason()).isEqualTo("LOW_CONFIDENCE");
        assertThat(routed.response().output().path("recommendedDecisionConcept").asText())
            .isEqualTo("INSUFFICIENT_INFORMATION");
        verify(gateway, times(2)).evaluate(any());
    }

    @Test
    void liveFastKeepsFastWhenConfident() {
        ComplianceSemanticRouter router = router("LIVE_FAST");
        when(gateway.evaluate(any())).thenReturn(response("COMPLIANT", 0.92, false));

        var routed = router.evaluate(request("BALANCED"), false);

        assertThat(routed.routing().liveProfile()).isEqualTo("FAST");
        assertThat(routed.routing().escalationReason()).isNull();
        verify(gateway, times(1)).evaluate(any());
    }

    @Test
    void shadowSurvivesFastFailure() {
        ComplianceSemanticRouter router = router("SHADOW");
        when(gateway.evaluate(any())).thenAnswer(invocation -> {
            SemanticRequest request = invocation.getArgument(0);
            if ("FAST".equals(request.modelProfile())) {
                throw new SemanticEvaluationException(
                    SemanticEvaluationFailureCode.LLM_UNAVAILABLE, "fast down", 0);
            }
            return response("COMPLIANT", 0.88, false);
        });

        var routed = router.evaluate(request("BALANCED"), false);

        assertThat(routed.response().output().path("recommendedDecisionConcept").asText())
            .isEqualTo("COMPLIANT");
        assertThat(routed.routing().comparison().path("shadowFailed").asBoolean()).isTrue();
    }

    private ComplianceSemanticRouter router(String mode) {
        return new ComplianceSemanticRouter(
            gateway, mapper, metrics, mode, "BALANCED", "FAST", 1024, 512, 0.70, 2);
    }

    private SemanticRequest request(String profile) {
        return new SemanticRequest(
            UUID.randomUUID(), "nanobase-spec-ai", profile,
            List.of("Return JSON"), mapper.createObjectNode(),
            Map.of("id", "req-1", "text", "Tier III"),
            List.of(), List.of(Map.of("id", "e1", "text", "evidence")),
            List.of("COMPLIANT", "NON_COMPLIANT", "INSUFFICIENT_INFORMATION"),
            1024, UUID.randomUUID());
    }

    private SemanticResponse response(String decision, double confidence, boolean review) {
        ObjectNode output = mapper.createObjectNode();
        output.put("recommendedDecisionConcept", decision);
        output.put("confidence", confidence);
        output.put("requiresManualReview", review);
        return new SemanticResponse(UUID.randomUUID(), output, 12, 10, 20);
    }
}
