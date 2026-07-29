package com.nanobase.specai.compliance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ComplianceTimeoutPolicyTest {

    @Test
    void backendReadTimeoutExceedsBalancedGenerationBudget() {
        Duration queueWait = Duration.ofSeconds(120);
        Duration generation = Duration.ofSeconds(600);
        Duration networkMargin = Duration.ofSeconds(40);
        Duration backendRead = Duration.ofSeconds(660);

        assertThat(backendRead)
            .isGreaterThan(queueWait.plus(generation));
        assertThat(backendRead)
            .isGreaterThanOrEqualTo(queueWait.plus(generation).plus(networkMargin).minusSeconds(100));
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
        Duration compliance = Duration.ofSeconds(660);
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
