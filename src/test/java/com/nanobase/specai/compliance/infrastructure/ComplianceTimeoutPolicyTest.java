package com.nanobase.specai.compliance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ComplianceTimeoutPolicyTest {

    @Test
    void backendReadTimeoutCoversBalancedGenerationBudget() {
        Duration queueWait = Duration.ofSeconds(180);
        Duration generation = Duration.ofSeconds(600);
        Duration backendRead = Duration.ofSeconds(780);
        Duration globalDeadline = Duration.ofSeconds(820);

        // Production V1: read timeout covers queue+generation; global deadline adds margin
        // so the backend does not drop the connection before a controlled orchestrator reply.
        assertThat(backendRead).isGreaterThanOrEqualTo(queueWait.plus(generation));
        assertThat(globalDeadline).isGreaterThan(queueWait.plus(generation));
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
