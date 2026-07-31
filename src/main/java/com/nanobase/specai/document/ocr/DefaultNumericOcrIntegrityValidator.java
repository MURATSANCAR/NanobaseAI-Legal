package com.nanobase.specai.document.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects OCR-ambiguous numeric tokens (0/O, 1/I/l, 5/S, separator confusion).
 * Does not invent corrections — marks ambiguity for confidence / review.
 */
@Component
public class DefaultNumericOcrIntegrityValidator implements NumericOcrIntegrityValidator {
    private static final Pattern MIXED_ALNUM = Pattern.compile(
        "(?iu)\\b(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{2,}\\b");
    private static final Pattern LOOKALIKE = Pattern.compile(
        "(?iu)\\b(?:[0O]{2,}|[1Il]{2,}|[5S]\\d|\\d[5S]|\\d[.,]\\d{3}[.,]\\d)\\b");

    @Override
    public NumericOcrValidationResult validate(NumericOcrContext context) {
        String text = context.rawText() == null ? "" : context.rawText().trim();
        if (text.isEmpty()) {
            return new NumericOcrValidationResult(false, List.of(), 0d);
        }
        List<String> issues = new ArrayList<>();
        Matcher mixed = MIXED_ALNUM.matcher(text);
        while (mixed.find()) {
            String token = mixed.group();
            if (containsLookalike(token)) {
                issues.add("AMBIGUOUS_ALNUM:" + token);
            }
        }
        Matcher lookalike = LOOKALIKE.matcher(text);
        while (lookalike.find()) {
            issues.add("LOOKALIKE_NUMERIC:" + lookalike.group());
        }
        if (text.contains(",") && text.contains(".")
            && text.matches("(?s).*\\d+[.,]\\d+[.,]\\d+.*")) {
            issues.add("AMBIGUOUS_DECIMAL_SEPARATOR");
        }
        double confidence = context.characterConfidence() == null
            ? 0.7d : context.characterConfidence();
        if (confidence < 0.55d && text.matches("(?s).*\\d.*")) {
            issues.add("LOW_CHAR_CONFIDENCE_NUMERIC");
        }
        boolean ambiguous = !issues.isEmpty();
        double penalty = Math.min(0.45d, 0.08d * issues.size());
        return new NumericOcrValidationResult(ambiguous, List.copyOf(issues), penalty);
    }

    private static boolean containsLookalike(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        // Adjacent digit/letter confusions only — do not flag clean codes like IP65.
        return upper.matches(".*(?:O\\d|\\dO|[IL]\\d|\\d[IL]|S\\d|\\dS).*");
    }
}
