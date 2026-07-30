package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedPage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Builds clauses from page text hierarchy when structured headings are missing.
 * Suppresses recurring header/footer signatures across pages.
 */
@Component
@Order(20)
public class TextHierarchyProvider implements ClauseSegmentationProvider {
    private static final Pattern PAGE_NUMBER = Pattern.compile(
        "^\\s*(?:sayfa\\s*)?\\d{1,4}(?:\\s*/\\s*\\d{1,4})?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
        "^\\s*(\\d+(?:\\.\\d+){0,4})[.)\\-\\s]+(.{3,160})$");
    private static final int MIN_PAGE_CHARS = 80;
    private static final int MAX_CLAUSE_CHARS = 1800;

    @Override
    public String providerCode() {
        return "TEXT_HIERARCHY";
    }

    @Override
    public ClauseSegmentationResult segment(ClauseSegmentationContext context) {
        if (context.currentClauses() != null && !context.currentClauses().isEmpty()) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        List<ExtractedPage> pages = context.extraction().pages();
        if (pages == null || pages.isEmpty()) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        List<RecurringElementDraft> recurring = detectRecurring(pages);
        var suppressed = recurring.stream()
            .filter(item -> item.pageRatio() >= 0.45d)
            .map(RecurringElementDraft::normalizedSignature)
            .collect(java.util.stream.Collectors.toSet());

        List<LayoutBlockDraft> blocks = new ArrayList<>();
        List<ExtractedClause> clauses = new ArrayList<>();
        int blockIndex = 0;
        int sortOrder = 0;
        for (ExtractedPage page : pages) {
            String raw = page.rawText() == null ? "" : page.rawText().trim();
            String normalized = normalize(page.normalizedText() == null ? raw : page.normalizedText());
            if (normalized.length() < MIN_PAGE_CHARS || suppressed.contains(normalized)) {
                continue;
            }
            if (PAGE_NUMBER.matcher(normalized).matches()) {
                continue;
            }
            List<String> segments = splitPage(normalized);
            for (String segment : segments) {
                if (segment.length() < 40) {
                    continue;
                }
                String title = titleOf(segment);
                String clauseNumber = numberOf(segment);
                blocks.add(new LayoutBlockDraft(
                    blockIndex, page.pageNumber(),
                    clauseNumber == null ? "PARAGRAPH" : "HEADING",
                    segment, segment, blockIndex, 0.72d));
                clauses.add(new ExtractedClause(
                    "text-hierarchy-" + blockIndex,
                    null,
                    clauseNumber == null ? "P" + page.pageNumber() + "-" + (sortOrder + 1) : clauseNumber,
                    title,
                    segment,
                    segment,
                    clauseNumber == null ? "PARAGRAPH" : "SECTION",
                    page.pageNumber(),
                    page.pageNumber(),
                    List.of(),
                    sha256(segment),
                    sortOrder++,
                    Map.of(
                        "segmentationProvider", providerCode(),
                        "fallback", false,
                        "sourceBlockIndex", blockIndex)));
                blockIndex++;
            }
        }
        if (clauses.isEmpty()) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        return new ClauseSegmentationResult(
            providerCode(), "1.0", clauses, blocks, recurring, false);
    }

    private List<String> splitPage(String text) {
        String[] paragraphs = text.split("\\n{2,}|\\r\\n{2,}");
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleaned = normalize(paragraph);
            if (cleaned.isBlank()) {
                continue;
            }
            if (NUMBERED_HEADING.matcher(cleaned).matches() && current.length() > 0) {
                out.add(trimToMax(current.toString()));
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(cleaned);
            if (current.length() >= MAX_CLAUSE_CHARS) {
                out.add(trimToMax(current.toString()));
                current.setLength(0);
            }
        }
        if (current.length() >= 40) {
            out.add(trimToMax(current.toString()));
        }
        if (out.isEmpty() && text.length() >= MIN_PAGE_CHARS) {
            // Hard-split oversized single-block pages.
            for (int i = 0; i < text.length(); i += MAX_CLAUSE_CHARS) {
                out.add(text.substring(i, Math.min(text.length(), i + MAX_CLAUSE_CHARS)).trim());
            }
        }
        return out;
    }

    private List<RecurringElementDraft> detectRecurring(List<ExtractedPage> pages) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (ExtractedPage page : pages) {
            String normalized = normalize(page.normalizedText() == null
                ? page.rawText() : page.normalizedText());
            if (normalized.length() < 12 || normalized.length() > 180) {
                continue;
            }
            // First/last short lines often headers/footers when repeated.
            String[] lines = normalized.split("\\s{2,}|\\n");
            if (lines.length == 0) {
                continue;
            }
            String first = normalize(lines[0]);
            if (first.length() >= 12 && first.length() <= 160) {
                counts.merge(first, 1, Integer::sum);
            }
        }
        int pageCount = Math.max(1, pages.size());
        List<RecurringElementDraft> out = new ArrayList<>();
        counts.forEach((signature, count) -> {
            double ratio = (double) count / pageCount;
            if (count >= 3 || ratio >= 0.45d) {
                out.add(new RecurringElementDraft(
                    signature,
                    PAGE_NUMBER.matcher(signature).matches() ? "PAGE_NUMBER" : "HEADER",
                    count,
                    ratio,
                    Math.min(1.0d, ratio)));
            }
        });
        return out;
    }

    private static String titleOf(String segment) {
        String first = segment.length() <= 120 ? segment : segment.substring(0, 120);
        var matcher = NUMBERED_HEADING.matcher(first);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        return first;
    }

    private static String numberOf(String segment) {
        var matcher = NUMBERED_HEADING.matcher(segment);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String trimToMax(String value) {
        if (value.length() <= MAX_CLAUSE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_CLAUSE_CHARS).trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
