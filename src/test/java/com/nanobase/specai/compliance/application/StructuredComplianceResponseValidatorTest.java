package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructuredComplianceResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuredComplianceResponseValidator validator =
        new StructuredComplianceResponseValidator();
    private final ComplianceDecisionSafetyGuard guard =
        new ComplianceDecisionSafetyGuard(mapper);

    @Test
    void compliantRequiresSupportingEvidence() {
        ObjectNode output = base("COMPLIANT");
        assertThat(validator.validate(output, Set.of("e1"), Set.of("COMPLIANT")).valid())
            .isFalse();
        output.putArray("supportingEvidenceIds").add("e1");
        assertThat(validator.validate(output, Set.of("e1"), Set.of("COMPLIANT")).valid())
            .isTrue();
    }

    @Test
    void imaginaryEvidenceIdsAreRejected() {
        ObjectNode output = base("COMPLIANT");
        output.putArray("supportingEvidenceIds").add("ghost");
        var result = validator.validate(output, Set.of("e1"), Set.of("COMPLIANT"));
        assertThat(result.valid()).isFalse();
        assertThat(result.failureCode())
            .isEqualTo(SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE);
    }

    @Test
    void distanceMissingIsInsufficientNotNonCompliant() {
        ObjectNode insufficient = base("INSUFFICIENT_INFORMATION");
        insufficient.putArray("missingInformation").add("veri merkezleri arasındaki mesafe");
        assertThat(validator.validate(insufficient, Set.of("e1"),
            Set.of("INSUFFICIENT_INFORMATION")).valid()).isTrue();
    }

    @Test
    void explicitContradictionAllowsNonCompliant() {
        ObjectNode output = base("NON_COMPLIANT");
        output.put("explicitContradiction", true);
        output.putArray("contradictingEvidenceIds").add("e1");
        assertThat(validator.validate(output, Set.of("e1"), Set.of("NON_COMPLIANT")).valid())
            .isTrue();
    }

    @Test
    void nonCompliantWithoutFlagsIsRemappedThenAcceptedAsInsufficient() {
        ObjectNode output = base("NON_COMPLIANT");
        output.putArray("supportingEvidenceIds").add("e1");
        ObjectNode safe = guard.normalize(output,
            "Veri merkezleri arasında en az 350 km mesafe olmalıdır.",
            java.util.List.of(java.util.Map.of("id", "e1", "text", "Kanıtta mesafe yok")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(validator.validate(safe, Set.of("e1"),
            Set.of("INSUFFICIENT_INFORMATION")).valid()).isTrue();
    }

    @Test
    void nonCompliantWithoutFlagsIsRejectedByValidator() {
        ObjectNode output = base("NON_COMPLIANT");
        output.putArray("supportingEvidenceIds").add("e1");
        var result = validator.validate(output, Set.of("e1"), Set.of("NON_COMPLIANT"));
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("explicitContradiction");
    }

    @Test
    void insufficientRequiresMissingElements() {
        ObjectNode output = base("INSUFFICIENT_INFORMATION");
        assertThat(validator.validate(output, Set.of("e1"),
            Set.of("INSUFFICIENT_INFORMATION")).valid()).isFalse();
        output.putArray("missingRequirementElements").add("mesafe");
        assertThat(validator.validate(output, Set.of("e1"),
            Set.of("INSUFFICIENT_INFORMATION")).valid()).isTrue();
    }

    private ObjectNode base(String decision) {
        ObjectNode output = mapper.createObjectNode();
        output.put("recommendedDecisionConcept", decision);
        output.put("confidence", 0.9);
        output.put("requiresManualReview", false);
        output.put("explicitContradiction", false);
        output.put("closedWorldApplied", false);
        output.putArray("conditionEvaluations");
        output.putArray("missingInformation");
        output.putArray("missingRequirementElements");
        output.putArray("warnings");
        return output;
    }
}
