package com.rag.embedding;

import com.rag.model.Chunk;
import com.rag.model.Embedding;

import java.util.List;

/**
 * Contract for producing dense vector embeddings from text.
 *
 * Two entry points — embedDocument vs embedQuery — exist to capture
 * query-document asymmetry present in models like BGE and E5:
 *
 *   BGE-large:  documents get no prefix; queries get
 *               "Represent this sentence for searching relevant passages: "
 *   E5-large:   documents get "passage: "; queries get "query: "
 *
 * If you embed a query with the document prefix (or vice versa), the vector
 * lands in the wrong region of the space and retrieval quality degrades
 * significantly — the model was trained to expect the prefix as a signal.
 *
 * Symmetric models (e.g. OpenAI text-embedding-3-*) ignore this distinction;
 * configuring empty prefixes for both paths gives identical behaviour.
 */
public interface EmbeddingEngine {

    /**
     * Embed a document chunk for indexing.
     * Applies the configured document prefix before embedding.
     */
    Embedding embedDocument(Chunk chunk);

    /**
     * Embed a search query.
     * Applies the configured query prefix before embedding.
     */
    Embedding embedQuery(String text);

    /**
     * Batch-embed document chunks.
     * Implementations SHOULD send a single API request for the whole batch
     * rather than N individual calls.
     */
    List<Embedding> embedDocuments(List<Chunk> chunks);

    /** Dimensionality of the output vectors — required to initialise the vector store. */
    int dimensions();

    /** Model identifier written into every Embedding for provenance tracking. */
    String modelId();
}
