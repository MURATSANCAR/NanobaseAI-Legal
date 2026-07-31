package com.nanobase.specai.workflow.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Structural visual rules for report PDFs. Pixel-perfect matching is not required.
 */
@Component
public class ReportVisualStructureValidator {

    public record VisualValidationResult(
        String statusCode,
        int pageCount,
        int overflowSignals,
        int cutTextSignals,
        int turkishGlyphIssues,
        List<String> issues
    ) {
    }

    public VisualValidationResult validate(byte[] pdfBytes, List<String> requiredSections) {
        List<String> issues = new ArrayList<>();
        if (pdfBytes == null || pdfBytes.length < 5
            || pdfBytes[0] != '%' || pdfBytes[1] != 'P'
            || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            issues.add("NOT_PDF");
            return new VisualValidationResult("FAIL", 0, 0, 0, 0, issues);
        }
        String asLatin = new String(pdfBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        int pageCount = countOccurrences(asLatin, "/Type /Page");
        if (pageCount <= 0) {
            pageCount = countOccurrences(asLatin, "/Type/Page");
        }
        int overflow = countOccurrences(asLatin.toLowerCase(Locale.ROOT), "overflow");
        int cutText = countOccurrences(asLatin.toLowerCase(Locale.ROOT), "placeholder")
            + countOccurrences(asLatin.toLowerCase(Locale.ROOT), "lorem ipsum");
        int turkishIssues = 0;
        // Missing Turkish glyphs often appear as replacement chars in extracted streams.
        turkishIssues += countOccurrences(asLatin, "\uFFFD");
        if (requiredSections != null) {
            String lower = asLatin.toLowerCase(Locale.ROOT);
            for (String section : requiredSections) {
                if (section != null && !section.isBlank()
                    && !lower.contains(section.toLowerCase(Locale.ROOT))) {
                    issues.add("MISSING_SECTION:" + section);
                }
            }
        }
        if (pageCount <= 0) {
            issues.add("NO_PAGES");
        }
        if (cutText > 0) {
            issues.add("PLACEHOLDER_OR_CUT_TEXT");
        }
        String status = issues.isEmpty() ? "PASS" : "FAIL";
        return new VisualValidationResult(
            status, pageCount, overflow, cutText, turkishIssues, List.copyOf(issues));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
