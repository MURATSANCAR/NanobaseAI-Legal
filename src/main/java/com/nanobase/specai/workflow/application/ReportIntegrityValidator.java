package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

public interface ReportIntegrityValidator {
    ReportValidationResult validate(byte[] content, String formatConceptCode,
                                    String reportName, JsonNode report);

    record ReportValidationResult(
        boolean ok,
        String errorCode,
        int pageCount,
        long fileSize,
        List<String> requiredSections,
        List<String> missingSections,
        String detail
    ) {
        static ReportValidationResult pass(int pageCount, long fileSize,
                                           List<String> requiredSections) {
            return new ReportValidationResult(true, null, pageCount, fileSize,
                requiredSections, List.of(), "ok");
        }

        static ReportValidationResult fail(String errorCode, long fileSize,
                                           List<String> required, List<String> missing,
                                           String detail) {
            return new ReportValidationResult(false, errorCode, 0, fileSize,
                required, missing, detail);
        }
    }
}

@Component
class DefaultReportIntegrityValidator implements ReportIntegrityValidator {
    private static final long MIN_PDF_BYTES = 1200L;

    @Override
    public ReportValidationResult validate(byte[] content, String formatConceptCode,
                                           String reportName, JsonNode report) {
        List<String> required = List.of(
            "PROJECT_SUMMARY", "DOCUMENT_LIST", "REQUIREMENTS", "COMPLIANCE_MATRIX");
        if (content == null || content.length == 0) {
            return ReportIntegrityValidator.ReportValidationResult.fail(
                "REPORT_DATA_EMPTY", 0, required, required, "empty artifact");
        }
        if (!"PDF".equalsIgnoreCase(formatConceptCode)) {
            return ReportIntegrityValidator.ReportValidationResult.pass(
                1, content.length, required);
        }
        String header = new String(content, 0, Math.min(8, content.length),
            StandardCharsets.US_ASCII);
        if (!header.startsWith("%PDF")) {
            return ReportIntegrityValidator.ReportValidationResult.fail(
                "REPORT_PDF_INVALID", content.length, required, List.of(),
                "missing PDF magic");
        }
        if (content.length < MIN_PDF_BYTES) {
            return ReportIntegrityValidator.ReportValidationResult.fail(
                "REPORT_PDF_INVALID", content.length, required, List.of(),
                "PDF below minimum integrity size");
        }
        String ascii = new String(content, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        if (!ascii.contains("/type /page") && !ascii.contains("/type/page")) {
            return ReportIntegrityValidator.ReportValidationResult.fail(
                "REPORT_PDF_INVALID", content.length, required, List.of(),
                "PDF has no page objects");
        }
        List<String> missing = new ArrayList<>();
        String text = ascii;
        if (!containsAny(text, "project", report.path("counts").path("projectId").asText(""))) {
            // project name/id should appear in payload-derived text
        }
        JsonNode counts = report.path("counts");
        if (!text.contains("requirement") && counts.path("requirements").asInt(0) > 0) {
            missing.add("REQUIREMENTS");
        }
        if (!text.contains("compliance") && counts.path("complianceEvaluations").asInt(0) > 0) {
            missing.add("COMPLIANCE_MATRIX");
        }
        if (!missing.isEmpty()) {
            return ReportIntegrityValidator.ReportValidationResult.fail(
                "REPORT_REQUIRED_SECTION_MISSING", content.length, required, missing,
                "required report sections missing from rendered PDF");
        }
        int pageCount = countOccurrences(ascii, "/type /page")
            + countOccurrences(ascii, "/type/page");
        pageCount = Math.max(1, pageCount);
        return ReportIntegrityValidator.ReportValidationResult.pass(
            pageCount, content.length, required);
    }

    private static boolean containsAny(String haystack, String a, String b) {
        return haystack.contains(a.toLowerCase(Locale.ROOT))
            || (b != null && !b.isBlank() && haystack.contains(b.toLowerCase(Locale.ROOT)));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
