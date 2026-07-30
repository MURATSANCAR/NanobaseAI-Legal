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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Last-resort segmentation: obligation-bearing sentences from page text.
 * Dynamic signal patterns — not institution-specific phrase lists.
 */
@Component
@Order(30)
public class ObligationAwareFallbackProvider implements ClauseSegmentationProvider {
    private static final Pattern OBLIGATION = Pattern.compile(
        "(?iu)([^.!?]{25,280}?(?:"
            + "zorundad[ıi]r|zorunludur|sağlanacakt[ıi]r|edilecektir|yap[ıi]lacakt[ıi]r|"
            + "bulunacakt[ıi]r|olacakt[ıi]r|i[cç]ermelidir|desteklemelidir|"
            + "kullan[ıi]lacakt[ıi]r|sunulacakt[ıi]r|teslim edilecektir|"
            + "shall|must|required|should ensure"
            + ")[^.!?]{0,100}[.!?])");
    private static final int MAX_CLAUSES = 40;

    @Override
    public String providerCode() {
        return "OBLIGATION_AWARE_FALLBACK";
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
        List<ExtractedClause> clauses = new ArrayList<>();
        List<LayoutBlockDraft> blocks = new ArrayList<>();
        int index = 0;
        for (ExtractedPage page : pages) {
            String text = normalize(page.normalizedText() == null
                ? page.rawText() : page.normalizedText());
            Matcher matcher = OBLIGATION.matcher(text);
            while (matcher.find() && clauses.size() < MAX_CLAUSES) {
                String sentence = normalize(matcher.group(1));
                if (sentence.length() < 40) {
                    continue;
                }
                blocks.add(new LayoutBlockDraft(
                    index, page.pageNumber(), "PARAGRAPH", sentence, sentence, index, 0.65d));
                clauses.add(new ExtractedClause(
                    "obligation-fallback-" + index,
                    null,
                    "O" + (index + 1),
                    sentence.length() > 100 ? sentence.substring(0, 100) : sentence,
                    sentence,
                    sentence,
                    "OBLIGATION",
                    page.pageNumber(),
                    page.pageNumber(),
                    List.of(),
                    sha256(sentence),
                    index,
                    Map.of(
                        "segmentationProvider", providerCode(),
                        "fallback", true,
                        "usedFallback", true)));
                index++;
            }
        }
        if (clauses.isEmpty()) {
            // Ultimate fallback: one bounded clause per substantial page.
            for (ExtractedPage page : pages) {
                String text = normalize(page.normalizedText() == null
                    ? page.rawText() : page.normalizedText());
                if (text.length() < 120) {
                    continue;
                }
                String body = text.length() > 1600 ? text.substring(0, 1600) : text;
                clauses.add(new ExtractedClause(
                    "page-fallback-" + page.pageNumber(),
                    null,
                    "PF" + page.pageNumber(),
                    "Sayfa " + page.pageNumber(),
                    body,
                    body,
                    "PAGE",
                    page.pageNumber(),
                    page.pageNumber(),
                    List.of(),
                    sha256(body),
                    clauses.size(),
                    Map.of(
                        "segmentationProvider", providerCode(),
                        "fallback", true,
                        "usedFallback", true)));
            }
        }
        if (clauses.isEmpty()) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        return new ClauseSegmentationResult(
            providerCode(), "1.0", clauses, blocks, List.of(), true);
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
