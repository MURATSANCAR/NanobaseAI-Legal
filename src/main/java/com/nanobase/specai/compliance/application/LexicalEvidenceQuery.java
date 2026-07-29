package com.nanobase.specai.compliance.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a lexical evidence query from requirement text.
 *
 * <p>{@code plainto_tsquery} uses AND semantics across every token, so long paraphrased
 * requirements never match short evidence fragments. This helper extracts significant
 * tokens and applies OR/prefix matching with a minimum hit threshold.
 */
public final class LexicalEvidenceQuery {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "the", "and", "or", "of", "to", "for", "in", "on", "is", "are",
        "be", "by", "with", "from", "that", "this", "as", "at", "it", "not", "no",
        "must", "shall", "should", "will", "can", "may", "have", "has", "been",
        "was", "were", "their", "its", "into", "over", "under", "than", "then",
        "also", "only", "all", "any", "each", "other", "such", "via", "per",
        "ve", "veya", "ile", "icin", "bir", "bu", "su", "da", "de", "ki", "mi",
        "en", "az", "olan", "olarak", "olmali", "olmalidir", "olunmalidir",
        "edilmelidir", "edilecektir", "yapilmalidir", "yapilacaktir",
        "saglanmalidir", "bulunmalidir", "sunacaktir", "karsilanmalidir",
        "sahip", "edinmelidir", "required", "mandatory", "least", "more", "less",
        "above", "below", "following", "include", "includes", "provide", "provides",
        "solution", "services", "service", "scope", "items", "main", "five"
    );
    private static final String[] SUFFIXES = {
        "sindan", "sinden", "sina", "sine", "sini", "ndan", "nden", "lara", "lere",
        "lar", "ler", "dir", "tir", "nin", "nun", "ini", "ina", "ine", "dan", "den",
        "si", "sı"
    };

    private final List<String> tokens;
    private final String tsQuery;
    private final int minimumHits;
    private final int queryTextLength;

    private LexicalEvidenceQuery(List<String> tokens, String tsQuery, int minimumHits,
                                 int queryTextLength) {
        this.tokens = List.copyOf(tokens);
        this.tsQuery = tsQuery;
        this.minimumHits = minimumHits;
        this.queryTextLength = queryTextLength;
    }

    public static LexicalEvidenceQuery from(String text) {
        String original = text == null ? "" : text;
        String normalized = fold(original);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (STOPWORDS.contains(token) || token.length() < 2) {
                continue;
            }
            unique.add(token);
            String stem = stripSuffix(token);
            if (stem.length() >= 3 && !STOPWORDS.contains(stem)) {
                unique.add(stem);
            }
        }
        List<String> tokens = new ArrayList<>(unique);
        if (tokens.isEmpty()) {
            return new LexicalEvidenceQuery(List.of(), "", 1, original.length());
        }
        int minimumHits = minimumHits(tokens.size());
        String tsQuery = tokens.stream()
            .map(LexicalEvidenceQuery::escapeTsToken)
            .map(token -> token + ":*")
            .reduce((left, right) -> left + " | " + right)
            .orElse("");
        return new LexicalEvidenceQuery(tokens, tsQuery, minimumHits, original.length());
    }

    public List<String> tokens() {
        return tokens;
    }

    public String tokenList() {
        return String.join(" ", tokens);
    }

    public String tsQuery() {
        return tsQuery;
    }

    public int minimumHits() {
        return minimumHits;
    }

    public int queryTextLength() {
        return queryTextLength;
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    public int hitCount(String fragmentText) {
        if (isEmpty() || fragmentText == null || fragmentText.isBlank()) {
            return 0;
        }
        String haystack = " " + fold(fragmentText) + " ";
        int hits = 0;
        for (String token : tokens) {
            if (haystack.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    public boolean matches(String fragmentText) {
        return hitCount(fragmentText) >= minimumHits;
    }

    static int minimumHits(int tokenCount) {
        if (tokenCount <= 1) {
            return 1;
        }
        if (tokenCount <= 4) {
            return 2;
        }
        return Math.min(3, Math.max(2, (tokenCount + 3) / 4));
    }

    static String fold(String value) {
        StringBuilder folded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            folded.appendCodePoint(switch (codePoint) {
                case 'İ', 'I', 'ı' -> 'i';
                case 'Ş', 'ş' -> 's';
                case 'Ğ', 'ğ' -> 'g';
                case 'Ü', 'ü' -> 'u';
                case 'Ö', 'ö' -> 'o';
                case 'Ç', 'ç' -> 'c';
                default -> Character.toLowerCase(codePoint);
            });
        }
        String normalized = Normalizer.normalize(folded.toString(), Normalizer.Form.NFKC);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return NON_ALNUM.matcher(normalized).replaceAll(" ").trim();
    }

    private static String stripSuffix(String token) {
        for (String suffix : SUFFIXES) {
            if (token.length() > suffix.length() + 3 && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    private static String escapeTsToken(String token) {
        return token.replace("'", "")
            .replace(":", "")
            .replace("&", "")
            .replace("|", "")
            .replace("!", "")
            .replace("(", "")
            .replace(")", "");
    }
}
