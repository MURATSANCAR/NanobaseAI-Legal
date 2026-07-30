package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Decision semantics regression anchors (350 km / ISO closed-world / Tier).
 */
class ComplianceDecisionSemanticsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ComplianceDecisionSafetyGuard guard =
        new ComplianceDecisionSafetyGuard(mapper);

    @Test
    void missingDistanceDefaultsToInsufficientInformation() {
        // Requirement: >=350 km. Evidence silent on distance.
        assertThat(ComplianceDecisionSafetyGuard.decide(false, false, true))
            .isEqualTo("INSUFFICIENT_INFORMATION");

        ObjectNode modelSaidNonCompliant = base("NON_COMPLIANT");
        ObjectNode safe = guard.normalize(
            modelSaidNonCompliant,
            "Veri merkezleri arasında en az 350 km mesafe olmalıdır.",
            List.of(Map.of("id", "e1", "text", "Mesafe bilgisi bulunmuyor.")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(safe.path("missingRequirementElements")).isNotEmpty();
    }

    @Test
    void explicitShortDistanceIsNonCompliant() {
        // Evidence: 120 km between data centers.
        assertThat(ComplianceDecisionSafetyGuard.decide(true, false, false))
            .isEqualTo("NON_COMPLIANT");

        ObjectNode output = base("NON_COMPLIANT");
        output.put("explicitContradiction", true);
        output.putArray("contradictingEvidenceIds").add("e1");
        ObjectNode safe = guard.normalize(
            output,
            "Veri merkezleri arasında en az 350 km mesafe olmalıdır.",
            List.of(Map.of("id", "e1", "text",
                "Veri merkezleri arasındaki mesafe 120 km")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("NON_COMPLIANT");
    }

    @Test
    void tierTwoEvidenceAgainstTierThreeIsNonCompliant() {
        assertThat(ComplianceDecisionSafetyGuard.decide(true, false, false))
            .isEqualTo("NON_COMPLIANT");

        ObjectNode output = base("NON_COMPLIANT");
        output.put("explicitContradiction", true);
        output.putArray("contradictingEvidenceIds").add("e1");
        ObjectNode safe = guard.normalize(
            output,
            "Veri merkezi Tier III olmalıdır.",
            List.of(Map.of("id", "e1", "text", "Tesis Tier II sertifikasına sahiptir.")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("NON_COMPLIANT");
    }

    @Test
    void missingIsoCertificatesWithoutClosedWorldIsInsufficient() {
        assertThat(ComplianceDecisionSafetyGuard.decide(false, false, true))
            .isEqualTo("INSUFFICIENT_INFORMATION");

        ObjectNode output = base("NON_COMPLIANT");
        ObjectNode safe = guard.normalize(
            output,
            "ISO 27001 + ISO 22301 + PCI DSS sertifikaları gereklidir.",
            List.of(Map.of("id", "e1", "text", "ISO 27001 sertifikası bulundu.")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("INSUFFICIENT_INFORMATION");
    }

    @Test
    void missingRequiredCertificateWithClosedWorldIsNonCompliant() {
        assertThat(ComplianceDecisionSafetyGuard.decide(false, true, true))
            .isEqualTo("NON_COMPLIANT");

        ObjectNode output = base("NON_COMPLIANT");
        output.put("closedWorldApplied", true);
        output.putArray("missingRequirementElements").add("ISO 22301");
        ObjectNode safe = guard.normalize(
            output,
            "ISO 27001 + ISO 22301 + PCI DSS sertifikaları gereklidir.",
            List.of(Map.of("id", "e1", "text", "ISO 27001 sertifikası bulundu.")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("NON_COMPLIANT");
    }

    @Test
    void explicitSupportIsCompliant() {
        assertThat(ComplianceDecisionSafetyGuard.decide(false, false, false))
            .isEqualTo("COMPLIANT");
    }

    @Test
    void numericContradictionClaimWithoutValueIsRemapped() {
        ObjectNode output = base("NON_COMPLIANT");
        output.put("explicitContradiction", true);
        ObjectNode safe = guard.normalize(
            output,
            "RTO en fazla 4 saat olmalıdır.",
            List.of(Map.of("id", "e1", "text", "Felaket kurtarma prosedürü mevcuttur.")));
        assertThat(safe.path("recommendedDecisionConcept").asText())
            .isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(safe.path("explicitContradiction").asBoolean()).isFalse();
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
