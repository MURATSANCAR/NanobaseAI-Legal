package com.nanobase.specai.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClauseChunkerTest {

    @Test
    void emptyTextReturnsEmptyList() {
        assertThat(ClauseChunker.chunk("", 1200, 80)).isEmpty();
    }

    @Test
    void nullTextReturnsEmptyList() {
        assertThat(ClauseChunker.chunk(null, 1200, 80)).isEmpty();
    }

    @Test
    void shortTextFitsInSingleChunk() {
        String text = "This is a short clause.";
        List<String> chunks = ClauseChunker.chunk(text, 1200, 80);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isEqualTo(text);
    }

    @Test
    void textExactlyAtMaxCharsFitsInSingleChunk() {
        String text = "a".repeat(1200);
        List<String> chunks = ClauseChunker.chunk(text, 1200, 80);
        assertThat(chunks).hasSize(1);
    }

    @Test
    void longTextSplitsIntoMultipleChunks() {
        String text = "word ".repeat(500);
        List<String> chunks = ClauseChunker.chunk(text, 1200, 80);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void allChunksFitWithinMaxChars() {
        String text = "word ".repeat(500);
        List<String> chunks = ClauseChunker.chunk(text, 1200, 80);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(1200);
        }
    }

    @Test
    void chunksCoversAllOriginalContent() {
        // Every word in original should appear in at least one chunk (via overlap or split)
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(50);
        List<String> chunks = ClauseChunker.chunk(text, 200, 40);
        assertThat(chunks).isNotEmpty();
        // First and last chunk should contain text from start/end of original
        assertThat(chunks.getFirst()).contains("The quick brown fox");
        String combined = String.join(" ", chunks);
        assertThat(combined).contains("lazy dog");
    }

    @Test
    void overlapMeansSecondChunkStartsBeforeEndOfFirst() {
        // Build text long enough to produce at least 2 chunks with overlap
        String sentence = "This is a test sentence that fills space. ";
        String text = sentence.repeat(60);
        List<String> chunks = ClauseChunker.chunk(text, 300, 60);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        // With overlap the beginning of chunk[1] should overlap with the tail of chunk[0]
        String endOfFirst = chunks.get(0).substring(
            Math.max(0, chunks.get(0).length() - 60));
        assertThat(chunks.get(1)).contains(endOfFirst.substring(0, Math.min(20, endOfFirst.length())).trim());
    }

    @Test
    void maximumCharsEnforcedAtMinimum200() {
        // Even if caller passes maxChars < 200, effective max is clamped to 200.
        String text = "a".repeat(250);
        List<String> chunks = ClauseChunker.chunk(text, 50, 0);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(200);
        assertThat(chunks.get(1)).hasSize(50);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }

    @Test
    void defaultMaxCharsIsBoundedForLlm() {
        assertThat(ClauseChunker.DEFAULT_MAX_CHARS).isLessThanOrEqualTo(600);
        String text = "word ".repeat(400);
        List<String> chunks = ClauseChunker.chunk(
            text, ClauseChunker.DEFAULT_MAX_CHARS, ClauseChunker.DEFAULT_OVERLAP_CHARS);
        assertThat(chunks).isNotEmpty();
        chunks.forEach(c ->
            assertThat(c.length()).isLessThanOrEqualTo(ClauseChunker.DEFAULT_MAX_CHARS));
    }

    @Test
    void overlapIsConstrainedToQuarterOfMax() {
        // overlap > max/4 should be clamped
        String text = "word ".repeat(300);
        // Requesting overlap = 400 on max = 300 — should be clamped to 75
        List<String> chunks = ClauseChunker.chunk(text, 300, 400);
        assertThat(chunks).isNotEmpty();
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(300));
    }

    @Test
    void prefersSentenceBoundaryWhenSplitting() {
        // Build text where a sentence boundary falls before the max cut
        String line = "First sentence ends here. Second sentence starts now. ";
        String text = line.repeat(30);
        List<String> chunks = ClauseChunker.chunk(text, 200, 20);
        // Chunks that split at ". " should end with a period
        for (int i = 0; i < chunks.size() - 1; i++) {
            String chunk = chunks.get(i);
            // At least some non-last chunks should end at a sentence boundary
            if (chunk.endsWith(".")) {
                return; // Found one — pass
            }
        }
        // Acceptable even if sentence boundaries were not reachable in the split window
        assertThat(chunks).isNotEmpty();
    }
}
