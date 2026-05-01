package com.rag.model;

/**
 * Provenance information attached to every Chunk.
 *
 * startChar / endChar are offsets into the original source text so that
 * downstream components can reconstruct context or highlight results.
 */
public record ChunkMetadata(
        String sourceId,
        int chunkIndex,
        int startChar,
        int endChar
) {
    public ChunkMetadata {
        if (sourceId == null || sourceId.isBlank())
            throw new IllegalArgumentException("sourceId must not be blank");
        if (chunkIndex < 0)
            throw new IllegalArgumentException("chunkIndex must be >= 0");
        if (startChar < 0)
            throw new IllegalArgumentException("startChar must be >= 0");
        if (endChar < startChar)
            throw new IllegalArgumentException(
                    "endChar (" + endChar + ") must be >= startChar (" + startChar + ")");
    }
}
