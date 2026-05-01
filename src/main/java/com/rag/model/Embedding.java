package com.rag.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * The output of embedding a single piece of text.
 *
 * chunkId     — references Chunk.id for document embeddings; "query:<hash>" for queries
 * vector      — L2-normalised float array; ready for cosine similarity (dot product of
 *               two unit vectors equals their cosine similarity)
 * model       — exact model identifier used to produce this vector; MUST match across
 *               all embeddings in a collection — mixing models corrupts the vector space
 * inputTokens — approximate token count at embedding time; use to detect silent truncation
 *               (most models silently drop tokens beyond their limit)
 *
 * Note on float[]:
 *   Java records use referential equality for arrays by default, so equals/hashCode
 *   are explicitly overridden to use Arrays.equals / Arrays.hashCode.
 */
public record Embedding(
        String chunkId,
        float[] vector,
        String model,
        int inputTokens
) {
    public Embedding {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(vector, "vector must not be null");
        Objects.requireNonNull(model, "model must not be null");
        if (vector.length == 0) throw new IllegalArgumentException("vector must not be empty");
        if (inputTokens < 0) throw new IllegalArgumentException("inputTokens must be >= 0");
    }

    public int dimensions() {
        return vector.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Embedding e)) return false;
        return inputTokens == e.inputTokens
                && chunkId.equals(e.chunkId)
                && Arrays.equals(vector, e.vector)
                && model.equals(e.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkId, Arrays.hashCode(vector), model, inputTokens);
    }

    @Override
    public String toString() {
        return "Embedding[chunkId=" + chunkId
                + ", dimensions=" + vector.length
                + ", model=" + model
                + ", inputTokens=" + inputTokens + "]";
    }
}
