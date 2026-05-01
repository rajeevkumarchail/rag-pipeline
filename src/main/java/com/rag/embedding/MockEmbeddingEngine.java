package com.rag.embedding;

import com.rag.model.Chunk;
import com.rag.model.Embedding;

import java.util.List;
import java.util.Random;

/**
 * Deterministic, zero-dependency embedding engine for tests and local development.
 *
 * Properties:
 *   - Deterministic: same text always produces the same vector (seeded RNG via hashCode)
 *   - Unit-normalised: vectors lie on the unit hypersphere, ready for cosine similarity
 *   - Asymmetric: query and document paths apply their respective prefixes before
 *     hashing, so a query vector ≠ the document vector for the same raw text when
 *     non-empty prefixes are configured — the same asymmetry as real BGE/E5 models
 *   - Different inputs → different vectors (collisions are possible but rare)
 *
 * Do NOT use in production — the vectors have no semantic meaning.
 */
public class MockEmbeddingEngine implements EmbeddingEngine {

    private final int dims;
    private final String queryPrefix;
    private final String documentPrefix;
    private final String model;

    public MockEmbeddingEngine(int dims, String queryPrefix, String documentPrefix) {
        if (dims <= 0) throw new IllegalArgumentException("dims must be positive");
        this.dims = dims;
        this.queryPrefix = queryPrefix == null ? "" : queryPrefix;
        this.documentPrefix = documentPrefix == null ? "" : documentPrefix;
        this.model = "mock-" + dims + "d";
    }

    @Override
    public Embedding embedDocument(Chunk chunk) {
        String text = documentPrefix + chunk.content();
        return new Embedding(chunk.id(), unitVector(text), model, estimateTokens(text));
    }

    @Override
    public Embedding embedQuery(String text) {
        String prefixed = queryPrefix + text;
        return new Embedding("query:" + text.hashCode(), unitVector(prefixed), model, estimateTokens(prefixed));
    }

    @Override
    public List<Embedding> embedDocuments(List<Chunk> chunks) {
        return chunks.stream().map(this::embedDocument).toList();
    }

    @Override
    public int dimensions() {
        return dims;
    }

    @Override
    public String modelId() {
        return model;
    }

    // ------------------------------------------------------------------ internals

    private float[] unitVector(String text) {
        Random rng = new Random(text.hashCode());
        float[] v = new float[dims];
        double norm = 0;
        for (int i = 0; i < dims; i++) {
            v[i] = rng.nextFloat() * 2 - 1;
            norm += (double) v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < dims; i++) v[i] /= (float) norm;
        return v;
    }

    private static int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
