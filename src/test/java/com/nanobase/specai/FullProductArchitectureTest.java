package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture guardrails for full-product correctness:
 * - document.domain classes must not reference Docling provider types
 * - ReportIntegrityValidator must be wired into DynamicReportingService
 */
class FullProductArchitectureTest {

    private final Path root = Path.of("").toAbsolutePath();
    private final Path srcMain = root.resolve(
        "src/main/java/com/nanobase/specai");

    // ── 1. document.domain isolation from provider adapters ──────────────────

    @Test
    void documentDomainClassesDoNotDependOnDocling() throws IOException {
        Path domainDir = srcMain.resolve("document/domain");
        assertThat(domainDir).exists();
        try (Stream<Path> paths = Files.walk(domainDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        assertThat(content)
                            .as("document.domain file %s must not import Docling types", file.getFileName())
                            .doesNotContain("docling")
                            .doesNotContain("Docling");
                    } catch (IOException exception) {
                        throw new AssertionError("Could not read file: " + file, exception);
                    }
                });
        }
    }

    @Test
    void documentDomainClassesDoNotDependOnOpenContracts() throws IOException {
        Path domainDir = srcMain.resolve("document/domain");
        try (Stream<Path> paths = Files.walk(domainDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        assertThat(content)
                            .as("document.domain file %s must not import OpenContracts types",
                                file.getFileName())
                            .doesNotContain("OpenContracts");
                    } catch (IOException exception) {
                        throw new AssertionError("Could not read file: " + file, exception);
                    }
                });
        }
    }

    // ── 2. ReportIntegrityValidator wired into DynamicReportingService ────────

    @Test
    void dynamicReportingServiceReferencesReportIntegrityValidator() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/workflow/application/DynamicReportingService.java");
        assertThat(content)
            .as("DynamicReportingService must reference ReportIntegrityValidator")
            .contains("ReportIntegrityValidator");
    }

    @Test
    void dynamicReportingServiceUsesIntegrityValidatorField() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/workflow/application/DynamicReportingService.java");
        assertThat(content)
            .as("DynamicReportingService must declare integrityValidator field")
            .contains("integrityValidator");
    }

    // ── 3. Knowledge stage tables referenced in processor ────────────────────

    @Test
    void knowledgeExtractionProcessorPersistsStages() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/knowledge/application/KnowledgeExtractionProcessor.java");
        assertThat(content)
            .as("KnowledgeExtractionProcessor must insert into knowledge_extraction_run_stage")
            .contains("knowledge_extraction_run_stage");
    }

    @Test
    void knowledgeExtractionProcessorSetsDocumentPurposeCode() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/knowledge/application/KnowledgeExtractionProcessor.java");
        assertThat(content)
            .as("KnowledgeExtractionProcessor must set document_purpose_code")
            .contains("document_purpose_code");
    }

    // ── 4. Clause chunks are persisted in RequirementExtractionProcessor ─────

    @Test
    void requirementExtractionProcessorPersistsClauseChunks() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/analysis/application/RequirementExtractionProcessor.java");
        assertThat(content)
            .as("RequirementExtractionProcessor must insert into clause_chunk")
            .contains("clause_chunk");
    }

    // ── 5. Document access audit wired ───────────────────────────────────────

    @Test
    void documentServicePersistsAccessAuditOnDownload() throws IOException {
        String content = read(
            "src/main/java/com/nanobase/specai/document/application/DocumentService.java");
        assertThat(content)
            .as("DocumentService must insert into document_access_url_audit")
            .contains("document_access_url_audit");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String read(String relative) throws IOException {
        return Files.readString(root.resolve(relative));
    }
}
