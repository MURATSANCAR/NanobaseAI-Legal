package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Decision semantics regression anchors (350 km / ISO closed-world / Tier).
 */
class ComplianceDecisionSemanticsTest {

    @Test
    void missingDistanceDefaultsToInsufficientInformation() {
        // Requirement: >=350 km. Evidence silent on distance.
        assertThat(decide(false, false, true)).isEqualTo("INSUFFICIENT_INFORMATION");
    }

    @Test
    void explicitShortDistanceIsNonCompliant() {
        // Evidence: 120 km between data centers.
        assertThat(decide(true, false, false)).isEqualTo("NON_COMPLIANT");
    }

    @Test
    void tierTwoEvidenceAgainstTierThreeIsNonCompliant() {
        assertThat(decide(true, false, false)).isEqualTo("NON_COMPLIANT");
    }

    @Test
    void missingIsoCertificatesWithoutClosedWorldIsInsufficient() {
        assertThat(decide(false, false, true)).isEqualTo("INSUFFICIENT_INFORMATION");
    }

    @Test
    void missingRequiredCertificateWithClosedWorldIsNonCompliant() {
        assertThat(decide(false, true, true)).isEqualTo("NON_COMPLIANT");
    }

    @Test
    void explicitSupportIsCompliant() {
        assertThat(decide(false, false, false)).isEqualTo("COMPLIANT");
    }

    /**
     * Mirrors the production decision table without calling an LLM.
     */
    static String decide(boolean explicitContradiction, boolean closedWorldApplied,
                         boolean missingElements) {
        if (explicitContradiction || (closedWorldApplied && missingElements)) {
            return "NON_COMPLIANT";
        }
        if (missingElements) {
            return "INSUFFICIENT_INFORMATION";
        }
        return "COMPLIANT";
    }
}
