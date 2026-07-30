package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComplianceDeploymentGuardrailsTest {
    @Test
    void poolMustCoverWorkersPlusConfiguredHeadroom() {
        assertThat(ComplianceDeploymentGuardrails.poolCapacityReason(5, 3, 2)).isNull();
        assertThat(ComplianceDeploymentGuardrails.poolCapacityReason(5, 4, 2))
            .contains("required=6");
        assertThat(ComplianceDeploymentGuardrails.poolCapacityReason(5, 8, 2))
            .contains("databasePoolSize=5");
    }

    @Test
    void validatedPhase6ProfileFitsPoolFive() {
        // Live PASS profile: pool=5, workers=3, headroom>=2
        assertThat(ComplianceDeploymentGuardrails.poolCapacityReason(5, 3, 2)).isNull();
    }
}
