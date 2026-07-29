package com.nanobase.specai.compliance.application;

/**
 * Vector embeddings are not wired in the current retrieval path. When a provider is
 * introduced, callers must fail loudly on dimension mismatch instead of returning an
 * empty candidate list.
 */
public final class EmbeddingDimensionGuard {
    private EmbeddingDimensionGuard() {
    }

    public static void requireCompatible(Integer queryDimension, Integer documentDimension) {
        if (queryDimension == null && documentDimension == null) {
            return;
        }
        if (queryDimension == null || documentDimension == null
            || !queryDimension.equals(documentDimension)) {
            throw new IllegalStateException(
                "Embedding dimension mismatch: query=" + queryDimension
                    + " document=" + documentDimension);
        }
    }
}
