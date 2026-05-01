package com.rag.embedding;

import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.model.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the EmbeddingEngine contract using MockEmbeddingEngine.
 * These tests verify the behavioural guarantees every implementation must satisfy.
 */
class EmbeddingEngineTest {

    private static final int DIMS = 8;
    private static final String QUERY_PREFIX = "query: ";
    private static final String DOC_PREFIX   = "passage: ";

    private EmbeddingEngine symmetric;   // no prefixes — like OpenAI
    private EmbeddingEngine asymmetric;  // prefixes — like E5/BGE

    @BeforeEach
    void setUp() {
        symmetric  = new MockEmbeddingEngine(DIMS, "", "");
        asymmetric = new MockEmbeddingEngine(DIMS, QUERY_PREFIX, DOC_PREFIX);
    }

    // ------------------------------------------------------------------ dimensions

    @Test
    void dimensions_matchesConfiguredValue() {
        assertThat(symmetric.dimensions()).isEqualTo(DIMS);
    }

    @Test
    void embedDocument_vectorHasCorrectDimensions() {
        Embedding e = symmetric.embedDocument(chunk("doc#0", "Hello world"));
        assertThat(e.vector()).hasSize(DIMS);
    }

    @Test
    void embedQuery_vectorHasCorrectDimensions() {
        assertThat(symmetric.embedQuery("What is RAG?").vector()).hasSize(DIMS);
    }

    // ------------------------------------------------------------------ unit normalisation

    @Test
    void embedDocument_vectorIsUnitNormalised() {
        float[] v = symmetric.embedDocument(chunk("id", "some text")).vector();
        assertThat(l2Norm(v)).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void embedQuery_vectorIsUnitNormalised() {
        float[] v = symmetric.embedQuery("what is rag?").vector();
        assertThat(l2Norm(v)).isCloseTo(1.0, within(1e-5));
    }

    // ------------------------------------------------------------------ determinism

    @Test
    void embedDocument_sameInput_sameVector() {
        Chunk c = chunk("doc#0", "Determinism test");
        float[] v1 = symmetric.embedDocument(c).vector();
        float[] v2 = symmetric.embedDocument(c).vector();
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void embedQuery_sameInput_sameVector() {
        float[] v1 = symmetric.embedQuery("same query").vector();
        float[] v2 = symmetric.embedQuery("same query").vector();
        assertThat(v1).isEqualTo(v2);
    }

    // ------------------------------------------------------------------ provenance

    @Test
    void embedDocument_chunkId_isPreserved() {
        Embedding e = symmetric.embedDocument(chunk("doc#42", "text"));
        assertThat(e.chunkId()).isEqualTo("doc#42");
    }

    @Test
    void embedDocument_modelId_isSet() {
        Embedding e = symmetric.embedDocument(chunk("id", "text"));
        assertThat(e.model()).isNotBlank();
        assertThat(e.model()).isEqualTo(symmetric.modelId());
    }

    @Test
    void embedDocument_inputTokens_isPositive() {
        assertThat(symmetric.embedDocument(chunk("id", "hello")).inputTokens()).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ batching

    @Test
    void embedDocuments_returnsOneEmbeddingPerChunk() {
        List<Chunk> chunks = List.of(
                chunk("a#0", "First"),
                chunk("a#1", "Second"),
                chunk("a#2", "Third"));
        assertThat(symmetric.embedDocuments(chunks)).hasSize(3);
    }

    @Test
    void embedDocuments_emptyList_returnsEmptyList() {
        assertThat(symmetric.embedDocuments(List.of())).isEmpty();
    }

    @Test
    void embedDocuments_preservesChunkIds() {
        List<Chunk> chunks = List.of(chunk("x#0", "A"), chunk("x#1", "B"));
        List<Embedding> embeddings = symmetric.embedDocuments(chunks);
        assertThat(embeddings).extracting(Embedding::chunkId)
                .containsExactly("x#0", "x#1");
    }

    @Test
    void embedDocuments_matchesSingleEmbedResults() {
        Chunk c = chunk("id", "Batch consistency check");
        Embedding single = symmetric.embedDocument(c);
        Embedding batched = symmetric.embedDocuments(List.of(c)).get(0);
        assertThat(single.vector()).isEqualTo(batched.vector());
    }

    // ------------------------------------------------------------------ query/document asymmetry

    @Test
    void symmetric_queryAndDocumentVectorsAreEqual_forSameText() {
        // With empty prefixes the same raw text produces the same vector regardless of role
        String text = "Paris is the capital of France";
        Chunk c = chunk("id", text);
        float[] docVec   = symmetric.embedDocument(c).vector();
        float[] queryVec = symmetric.embedQuery(text).vector();
        assertThat(docVec).isEqualTo(queryVec);
    }

    @Test
    void asymmetric_queryAndDocumentVectorsAreDifferent_forSameText() {
        // With different prefixes the same raw text produces different vectors —
        // this is intentional: the model is steered toward the right retrieval direction
        String text = "Paris is the capital of France";
        Chunk c = chunk("id", text);
        float[] docVec   = asymmetric.embedDocument(c).vector();
        float[] queryVec = asymmetric.embedQuery(text).vector();
        assertThat(docVec).isNotEqualTo(queryVec);
    }

    // ------------------------------------------------------------------ helpers

    private static Chunk chunk(String id, String content) {
        int len = content.length();
        return new Chunk(id, content, new ChunkMetadata("src", 0, 0, len));
    }

    private static double l2Norm(float[] v) {
        double sum = 0;
        for (float f : v) sum += (double) f * f;
        return Math.sqrt(sum);
    }
}
