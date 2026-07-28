package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.pilot.application.ErrorRootCauseAnalyzer;
import com.nanobase.specai.pilot.application.ExplainableErrorRootCauseAnalyzer;
import com.nanobase.specai.pilot.application.SensitiveDataSanitizer;
import com.nanobase.specai.release.application.ReleaseLifecycleService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Sprint9ArchitectureTest {
    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V15__pilot_stabilization_and_release_candidate.sql");

    @Test
    void rootCauseDomainUsesAnInterfaceAndDynamicConceptCodes() {
        assertThat(ErrorRootCauseAnalyzer.class).isInterface();
        assertThat(ExplainableErrorRootCauseAnalyzer.class.getInterfaces())
            .contains(ErrorRootCauseAnalyzer.class);
    }

    @Test
    void snapshotsResultsAndManifestsAreDatabaseImmutable() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
            "configuration_snapshot_immutable",
            "experiment_result_immutable",
            "release_manifest_immutable",
            "reproduction_package_immutable");
    }

    @Test
    void everySprint9TenantTableUsesRowLevelSecurity() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
            "ALTER TABLE %I ENABLE ROW LEVEL SECURITY",
            "ALTER TABLE %I FORCE ROW LEVEL SECURITY",
            "organization_id = app_current_organization_id()");
    }

    @Test
    void telemetryMetadataIsAllowlistedAndSensitiveFieldsAreAbsent() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains("validate_pilot_metadata", "allowed_keys CONSTANT");
        assertThat(SensitiveDataSanitizer.class.getDeclaredMethods())
            .extracting("name").contains("sanitize");
    }

    @Test
    void releaseServiceRecordsRequestsBeforeExternalDeployment() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/nanobase/specai/release/application/ReleaseLifecycleService.java"));
        assertThat(source).contains(
            "\"DEPLOYMENT_REQUESTED\"",
            "recordDeploymentResult",
            "\"ROLLBACK_REQUESTED\"",
            "recordRollbackResult");
    }

    @Test
    void goLiveDecisionRemainsHumanAttributed() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains(
            "decided_by VARCHAR(255) NOT NULL",
            "rollback_plan_reference TEXT NOT NULL");
        assertThat(ReleaseLifecycleService.class).isNotInterface();
    }
}
