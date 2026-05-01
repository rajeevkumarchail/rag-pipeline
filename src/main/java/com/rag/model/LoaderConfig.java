package com.rag.model;

/**
 * Immutable configuration for the document loading and chunking pipeline.
 *
 * Invariants enforced at construction time:
 *   chunkSize  > 0
 *   chunkOverlap >= 0
 *   chunkOverlap < chunkSize   (overlap must fit inside a chunk)
 *   minChunkSize >= 0
 */
public record LoaderConfig(
        int chunkSize,
        int chunkOverlap,
        int minChunkSize
) {
    public LoaderConfig {
        if (chunkSize <= 0)
            throw new IllegalArgumentException("chunkSize must be positive, got: " + chunkSize);
        if (chunkOverlap < 0)
            throw new IllegalArgumentException("chunkOverlap must be >= 0, got: " + chunkOverlap);
        if (chunkOverlap >= chunkSize)
            throw new IllegalArgumentException(
                    "chunkOverlap (" + chunkOverlap + ") must be < chunkSize (" + chunkSize + ")");
        if (minChunkSize < 0)
            throw new IllegalArgumentException("minChunkSize must be >= 0, got: " + minChunkSize);
    }

    /** Sensible defaults: 500-char chunks, 50-char overlap, discard anything under 20 chars. */
    public static LoaderConfig defaults() {
        return new LoaderConfig(500, 50, 20);
    }
}
