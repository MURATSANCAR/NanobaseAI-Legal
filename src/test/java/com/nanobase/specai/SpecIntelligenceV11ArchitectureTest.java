package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SpecIntelligenceV11ArchitectureTest {

    private final Path srcMain = Path.of("").toAbsolutePath()
        .resolve("src/main/java/com/nanobase/specai");

    @Test
    void documentRouterDoesNotHardcodeInstitutionNames() throws IOException {
        String content = Files.readString(
            srcMain.resolve("document/capability/DefaultDocumentProcessingRouter.java"));
        assertThat(content.toLowerCase())
            .doesNotContain("dsi")
            .doesNotContain("devlet su")
            .doesNotContain("iso9001");
    }

    @Test
    void capabilityPackageDoesNotDependOnComplianceLeaseTypes() throws IOException {
        Path dir = srcMain.resolve("document/capability");
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    assertThat(content)
                        .as(file.getFileName().toString())
                        .doesNotContain("ComplianceClaim")
                        .doesNotContain("leaseGeneration")
                        .doesNotContain("RedisModelCapacity");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
        }
    }

    @Test
    void objectDeliveryStrategyRejectsHostnameRewritePattern() throws IOException {
        String content = Files.readString(
            srcMain.resolve("document/delivery/ProfileAwareObjectDeliveryStrategy.java"));
        assertThat(content)
            .doesNotContain(".replace(\"minio\"")
            .doesNotContain("replace(internal");
        assertThat(content).contains("BACKEND_PROXY_ONLY");
    }

    @Test
    void v33MigrationExistsAndDoesNotAlterV28() throws IOException {
        Path v33 = Path.of("src/main/resources/db/migration/V33__spec_intelligence_v11_foundations.sql");
        Path v28 = Path.of("src/main/resources/db/migration/V28__compliance_lease_generation.sql");
        assertThat(v33).exists();
        assertThat(v28).exists();
        String v33Content = Files.readString(v33);
        assertThat(v33Content)
            .contains("document_capability_profile")
            .contains("document_table_cell")
            .contains("requirement_extraction_timing")
            .doesNotContain("ALTER TABLE compliance_claim_lease");
    }

    @Test
    void knowledgeValidityIsDeterministicComponent() throws IOException {
        String content = Files.readString(
            srcMain.resolve("knowledge/validity/DeterministicKnowledgeValidityEvaluator.java"));
        assertThat(content).contains("LocalDate");
        assertThat(content.toLowerCase()).doesNotContain("openai").doesNotContain("chat");
    }
}
