package com.nanobase.specai.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.workflow.application.ReportIntegrityValidator.ReportValidationResult;
import com.nanobase.specai.workflow.application.SafeReportRenderers.PdfRenderer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultReportIntegrityValidatorTest {

    private ReportIntegrityValidator validator;
    private PdfRenderer pdfRenderer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validator = new DefaultReportIntegrityValidator();
        pdfRenderer = new PdfRenderer();
        mapper = new ObjectMapper();
    }

    // ── null / empty content ──────────────────────────────────────────────────

    @Test
    void nullContentFails() {
        ReportValidationResult result = validator.validate(
            null, "PDF", "test-report", mapper.createObjectNode());
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("REPORT_DATA_EMPTY");
    }

    @Test
    void emptyContentFails() {
        ReportValidationResult result = validator.validate(
            new byte[0], "PDF", "test-report", mapper.createObjectNode());
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("REPORT_DATA_EMPTY");
    }

    // ── PDF-specific validations ──────────────────────────────────────────────

    @Test
    void tinyPdfBelowMinimumSizeFails() {
        // Craft a byte array with PDF magic but too small (< 1200 bytes)
        byte[] tinyPdf = "%PDF-1.4 tiny".getBytes(StandardCharsets.US_ASCII);
        assertThat(tinyPdf.length).isLessThan(1200);
        ReportValidationResult result = validator.validate(
            tinyPdf, "PDF", "tiny-report", mapper.createObjectNode());
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("REPORT_PDF_INVALID");
    }

    @Test
    void contentWithoutPdfMagicFails() {
        byte[] notPdf = "Not a PDF at all — no magic header.".repeat(100)
            .getBytes(StandardCharsets.US_ASCII);
        ReportValidationResult result = validator.validate(
            notPdf, "PDF", "bad-report", mapper.createObjectNode());
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("REPORT_PDF_INVALID");
    }

    @Test
    void nonPdfFormatPassesWithOnePageCount() {
        byte[] content = "some docx content here".getBytes(StandardCharsets.UTF_8);
        ReportValidationResult result = validator.validate(
            content, "DOCX", "report", mapper.createObjectNode());
        assertThat(result.ok()).isTrue();
        assertThat(result.pageCount()).isEqualTo(1);
    }

    // ── good multi-section PDF from PdfRenderer passes ────────────────────────

    @Test
    void goodMultiSectionPdfPassesValidation() {
        ObjectNode report = buildGoodReport();
        ReportFormatRenderer.RenderedReport rendered = pdfRenderer.render("Test Report", report);
        byte[] bytes = rendered.content();

        ReportValidationResult result = validator.validate(bytes, "PDF", "Test Report", report);

        assertThat(result.ok())
            .as("Multi-section PDF rendered by PdfRenderer should pass integrity check")
            .isTrue();
        assertThat(result.pageCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.fileSize()).isEqualTo(bytes.length);
        assertThat(result.missingSections()).isEmpty();
    }

    @Test
    void renderedPdfContainsPdfMagicHeader() {
        ObjectNode report = buildGoodReport();
        byte[] bytes = pdfRenderer.render("Compliance Report", report).content();
        assertThat(bytes.length).isGreaterThan(1200);
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void pdfWithRequirementsCountPassesRequirementsSectionCheck() {
        ObjectNode report = mapper.createObjectNode();
        report.put("projectId", "proj-1");
        report.put("snapshotId", "snap-1");
        ObjectNode counts = report.putObject("counts");
        counts.put("requirements", 5);
        counts.put("complianceEvaluations", 0);
        counts.put("risks", 0);
        counts.put("conflicts", 0);
        counts.put("ambiguities", 0);
        counts.put("openTasks", 0);
        counts.put("pendingApprovals", 0);
        counts.put("clarifications", 0);

        byte[] bytes = pdfRenderer.render("Requirements Report", report).content();
        ReportValidationResult result = validator.validate(bytes, "PDF", "Requirements Report", report);
        // The renderer outputs "requirements: 5" which should satisfy the requirement check
        assertThat(result.ok()).isTrue();
    }

    @Test
    void validationResultPassCarriesRequiredSections() {
        ObjectNode report = buildGoodReport();
        byte[] bytes = pdfRenderer.render("Full Report", report).content();
        ReportValidationResult result = validator.validate(bytes, "PDF", "Full Report", report);
        assertThat(result.requiredSections())
            .contains("PROJECT_SUMMARY", "DOCUMENT_LIST", "REQUIREMENTS", "COMPLIANCE_MATRIX");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ObjectNode buildGoodReport() {
        ObjectNode report = mapper.createObjectNode();
        report.put("projectId", "11111111-0000-0000-0000-000000000001");
        report.put("snapshotId", "22222222-0000-0000-0000-000000000001");
        ObjectNode counts = report.putObject("counts");
        counts.put("requirements", 10);
        counts.put("complianceEvaluations", 8);
        counts.put("risks", 3);
        counts.put("conflicts", 0);
        counts.put("ambiguities", 0);
        counts.put("openTasks", 2);
        counts.put("pendingApprovals", 0);
        counts.put("clarifications", 1);
        return report;
    }
}
