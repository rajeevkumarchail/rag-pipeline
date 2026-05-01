package com.rag.model;

import java.util.Objects;

/**
 * A single text segment ready for embedding.
 *
 * id      — stable identifier, "<sourceId>#<chunkIndex>", safe to use as a vector-store key
 * content — the raw text that will be embedded
 * metadata — provenance: where this came from and where in the source it lives
 */
public record Chunk(
        String id,
        String content,
        ChunkMetadata metadata
) {
    public Chunk {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (id.isBlank())
            throw new IllegalArgumentException("id must not be blank");
    }
}
