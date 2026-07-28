package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionHardeningArchitectureTest {
    private final Path root = Path.of("").toAbsolutePath();

    @Test
    void productionImagesNeverUseLatest() throws IOException {
        String compose = read("compose.yaml") + read("compose.production.yaml");
        assertThat(compose).doesNotContain(":latest");
    }

    @Test
    void applicationRuntimeImagesDeclareNonRootUserAndHealthcheck() throws IOException {
        for (String file : List.of(
            "Dockerfile",
            "services/document-intelligence/Dockerfile",
            "services/ai-orchestrator/Dockerfile",
            "frontend/Dockerfile"
        )) {
            String dockerfile = read(file);
            assertThat(dockerfile).contains("USER ");
            assertThat(dockerfile).contains("HEALTHCHECK");
            assertThat(dockerfile).doesNotContain("USER root").doesNotContain("USER 0\n");
        }
    }

    @Test
    void productionConfigDoesNotProvideDefaultSecrets() throws IOException {
        String production = read("src/main/resources/application-production.yml");
        assertThat(production).doesNotContain("PASSWORD:")
            .doesNotContain("SECRET:")
            .doesNotContain("TOKEN:");
        assertThat(production).doesNotContain("${DATABASE_PASSWORD:");
    }

    @Test
    void modelAndParserRuntimeAreNotPublishedToUserNetwork() throws IOException {
        String compose = read("compose.yaml");
        assertThat(compose).doesNotContain("8092:8090")
            .doesNotContain("\"127.0.0.1:8090:8090\"");
    }

    @Test
    void tenantScopedProductionTablesUseForcedRls() throws IOException {
        String migration = read(
            "src/main/resources/db/migration/V14__production_hardening.sql");
        assertThat(migration).contains("ENABLE ROW LEVEL SECURITY")
            .contains("FORCE ROW LEVEL SECURITY")
            .contains("app_current_organization_id()");
    }

    @Test
    void reportSnapshotsAndQualityGatesAreImmutableInputs() throws IOException {
        String workflow = read(
            "src/main/resources/db/migration/V13__dynamic_workflow_task_reporting.sql");
        String production = read(
            "src/main/resources/db/migration/V14__production_hardening.sql");
        assertThat(workflow).contains("CREATE TABLE report_data_snapshot");
        assertThat(production).contains("CREATE TABLE quality_gate_definition")
            .contains("CREATE TABLE quality_gate_version");
    }

    @Test
    void offlineInstallationCannotPullFromInternet() throws IOException {
        assertThat(read("scripts/install-offline.sh")).contains("--pull never");
    }

    @Test
    void featureFlagsAreSeparatedFromAuthorizationCode() throws IOException {
        String security = read(
            "src/main/java/com/nanobase/specai/shared/security/SecurityConfig.java");
        assertThat(security).doesNotContain("FeatureFlagService")
            .doesNotContain("feature_definition");
    }

    private String read(String relative) throws IOException {
        return Files.readString(root.resolve(relative));
    }
}
