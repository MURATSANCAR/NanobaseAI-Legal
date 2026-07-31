package com.nanobase.specai.analysis.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Policy-driven clause text chunking so LLM extraction never receives unbounded page text.
 */
public final class ClauseChunker {
    public static final int DEFAULT_MAX_CHARS = 500;
    public static final int DEFAULT_OVERLAP_CHARS = 40;
    /** Hard cap so one oversized clause cannot monopolize the model slot. */
    public static final int MAX_CHUNKS_PER_CLAUSE = 1;

    private ClauseChunker() {
    }

    public static List<String> chunk(String text, int maxChars, int overlapChars) {
        return chunk(text, maxChars, overlapChars, MAX_CHUNKS_PER_CLAUSE);
    }

    public static List<String> chunk(String text, int maxChars, int overlapChars, int maxChunks) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        int max = Math.max(200, maxChars);
        int overlap = Math.max(0, Math.min(overlapChars, max / 4));
        int limit = Math.max(1, maxChunks);
        if (normalized.length() <= max) {
            return List.of(normalized);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < limit) {
            int end = Math.min(normalized.length(), start + max);
            if (end < normalized.length()) {
                int split = normalized.lastIndexOf(". ", end);
                if (split > start + max / 3) {
                    end = split + 1;
                }
            }
            chunks.add(normalized.substring(start, end).trim());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
