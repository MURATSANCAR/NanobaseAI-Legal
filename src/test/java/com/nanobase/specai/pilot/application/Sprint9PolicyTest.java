package com.nanobase.specai.pilot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.release.application.ReleaseGatePolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Sprint9PolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rootCauseSuggestionIsExplainableAndRequiresHumanApproval() {
        var analyzer = new ExplainableErrorRootCauseAnalyzer();
        var signals = mapper.createObjectNode()
            .put("parentClauseMissing", true)
            .put("lowOcrQuality", 0.2);

        RootCauseAnalysisResult result = analyzer.analyze(
            new ErrorAnalysisContext("WRONG_GROUNDING", "MODEL_ERROR", "HIGH",
                signals, Map.of()),
            new ErrorAnalysisPolicyVersion("TEST", 3,
                Map.of("parentClauseMissing", 1.0, "lowOcrQuality", 0.5), 0.5));

        assertThat(result.primaryCauseConcept()).isEqualTo("CONTEXT_SELECTION");
        assertThat(result.contributingFactors()).isNotEmpty();
        assertThat(result.recommendedInvestigationAreas()).contains("CONTEXT_POLICY");
        assertThat(result.explanation()).contains("Policy TEST v3");
        assertThat(result.humanApprovalRequired()).isTrue();
    }

    @Test
    void analyzerDoesNotPretendLowSignalSuggestionIsCertain() {
        var analyzer = new ExplainableErrorRootCauseAnalyzer();
        RootCauseAnalysisResult result = analyzer.analyze(
            new ErrorAnalysisContext("MISSING_FEATURE", "FEATURE_REQUEST", "LOW",
                mapper.createObjectNode(), Map.of()),
            new ErrorAnalysisPolicyVersion("TEST", 1, Map.of(), 0.5));

        assertThat(result.confidence()).isLessThan(0.5);
        assertThat(result.humanApprovalRequired()).isTrue();
    }

    @Test
    void securityAndTenantSignalsAreAlwaysReleaseBlockers() {
        var policy = new ErrorPriorityPolicy();

        var decision = policy.evaluate(30, 10, "LOW", "FRONTEND",
            true, false, false);

        assertThat(decision.releaseBlocker()).isTrue();
        assertThat(decision.priorityScore()).isGreaterThan(25);
    }

    @Test
    void priorityPolicyRejectsInvalidScores() {
        var policy = new ErrorPriorityPolicy();

        assertThatThrownBy(() -> policy.evaluate(101, 0, "LOW", "FRONTEND",
            false, false, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitizerRemovesForbiddenFieldsAndMasksTokensAndEmail() {
        var sanitizer = new SensitiveDataSanitizer(mapper);
        var input = mapper.createObjectNode();
        input.put("documentText", "commercial secret");
        input.put("safeCode", "mail user@example.com bearer abc.def.ghi");
        input.set("nested", mapper.createObjectNode().put("signed_url", "https://secret"));

        var result = sanitizer.sanitize(input);

        assertThat(result.value().has("documentText")).isFalse();
        assertThat(result.value().path("nested").has("signed_url")).isFalse();
        assertThat(result.value().path("safeCode").asText())
            .contains("[REDACTED_EMAIL]", "[REDACTED_TOKEN]");
        assertThat(result.contentHash()).hasSize(64);
        assertThat(result.removedPaths()).hasSize(2);
    }

    @Test
    void releaseGateFailsClosedForMissingEvidence() {
        var policy = new ReleaseGatePolicy();

        var evaluation = policy.evaluate(
            List.of("BUILD", "SECURITY"),
            Map.of("BUILD", "PASS", "SECURITY", "NOT_RUN"),
            false, 1, false);

        assertThat(evaluation.eligible()).isFalse();
        assertThat(evaluation.missingEvidence())
            .contains("RELEASE_MANIFEST", "OPEN_RELEASE_BLOCKERS=1",
                "SECURITY:NOT_RUN", "HUMAN_APPROVAL");
    }

    @Test
    void gateWaiverRequiresReasonControlsAndAuthorization() {
        var policy = new ReleaseGatePolicy();

        assertThatThrownBy(() -> policy.validateWaiver(
            "WAIVED", "temporary", 0, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateWaiver(
            "WAIVED", "temporary", 1, false))
            .isInstanceOf(IllegalArgumentException.class);

        policy.validateWaiver("WAIVED", "approved exception", 1, true);
    }
}
