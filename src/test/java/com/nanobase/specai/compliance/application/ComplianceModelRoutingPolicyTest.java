package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.compliance.application.ComplianceModelRoutingPolicy.MatchType;
import com.nanobase.specai.compliance.application.ComplianceModelRoutingPolicy.Route;
import com.nanobase.specai.compliance.application.ComplianceModelRoutingPolicy.RoutingInput;
import org.junit.jupiter.api.Test;

class ComplianceModelRoutingPolicyTest {
    private final ComplianceModelRoutingPolicy shadow = ComplianceModelRoutingPolicy.defaults();
    private final ComplianceModelRoutingPolicy liveFast =
        new ComplianceModelRoutingPolicy(0.80, 0.95, true);

    @Test
    void zeroCandidatesSkipLlm() {
        assertThat(shadow.route(input(0, "MEDIUM", false, false, 0.99, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.NO_LLM_REQUIRED);
    }

    @Test
    void candidatesUseFastPrecheckBalancedFinalInShadow() {
        assertThat(shadow.route(input(2, "MEDIUM", false, false, 0.97, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.FAST_PRECHECK_BALANCED_FINAL);
    }

    @Test
    void highCriticalityGoesBalancedOnly() {
        assertThat(shadow.route(input(1, "HIGH", false, false, 0.99, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.BALANCED_ONLY);
    }

    @Test
    void conflictingCandidatesGoBalancedOnly() {
        assertThat(shadow.route(input(2, "MEDIUM", true, false, 0.99, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.BALANCED_ONLY);
    }

    @Test
    void lowFastConfidenceEscalates() {
        assertThat(shadow.route(input(1, "MEDIUM", false, false, 0.5, MatchType.PARTIAL_MATCH)))
            .isEqualTo(Route.BALANCED_ESCALATION);
    }

    @Test
    void topicOnlyEscalates() {
        assertThat(shadow.route(input(1, "MEDIUM", false, false, 0.9, MatchType.TOPIC_ONLY)))
            .isEqualTo(Route.BALANCED_ESCALATION);
    }

    @Test
    void liveFastAllowsFastOnlyWhenGatesPass() {
        assertThat(liveFast.route(input(1, "MEDIUM", false, false, 0.97, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.FAST_ONLY);
        assertThat(liveFast.route(input(1, "MEDIUM", false, false, 0.90, MatchType.EXPLICIT_SUPPORT)))
            .isEqualTo(Route.FAST_PRECHECK_BALANCED_FINAL);
    }

    private static RoutingInput input(int candidates, String criticality, boolean conflict,
                                      boolean closedWorld, double confidence, MatchType match) {
        return new RoutingInput("req-1", candidates, criticality, conflict, closedWorld,
            confidence, match);
    }
}
