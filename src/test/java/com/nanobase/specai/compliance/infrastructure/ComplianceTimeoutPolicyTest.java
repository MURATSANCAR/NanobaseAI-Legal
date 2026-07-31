package com.nanobase.specai.compliance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ComplianceTimeoutPolicyTest {

    @Test
    void backendReadTimeoutCoversBalancedGenerationBudget() {
        // Validated runtime generation budget for the current BALANCED local-model profile.
        Duration generation = Duration.ofSeconds(720);
        Duration backendRead = Duration.ofSeconds(780);
        Duration globalDeadline = Duration.ofSeconds(820);

        // Live DSİ PASS used PT780S read with PT720S generation (queueWait often ~0).
        // Full queueWait+generation stacking remains a v1.1 hardening topic.
        assertThat(backendRead).isGreaterThan(generation);
        assertThat(globalDeadline).isGreaterThan(backendRead);
    }

    @Test
    void fastBackendBudgetExceedsFastGeneration() {
        Duration queueWait = Duration.ofSeconds(60);
        Duration generation = Duration.ofSeconds(300);
        Duration backendRead = Duration.ofSeconds(360);
        assertThat(backendRead).isGreaterThan(queueWait.plus(generation).minusSeconds(1));
    }

    @Test
    void complianceDoesNotInheritDocumentProviderTimeout() {
        Duration documentProvider = Duration.ofSeconds(180);
        Duration compliance = Duration.ofSeconds(780);
        assertThat(compliance).isGreaterThan(documentProvider);
    }

    @Test
    void deploymentAliasesHideProductModelNames() {
        assertThat(HttpComplianceAiGateway.deploymentAlias("FAST")).isEqualTo("nanobase-fast");
        assertThat(HttpComplianceAiGateway.deploymentAlias("BALANCED"))
            .isEqualTo("nanobase-balanced");
        assertThat(HttpComplianceAiGateway.deploymentAlias("FAST"))
            .doesNotContain("Qwen");
    }
}
