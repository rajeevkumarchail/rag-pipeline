package com.rag.embedding;

import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.model.Embedding;
import com.rag.service.EmbeddingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live smoke-test against a locally running Ollama instance.
 *
 * Skipped automatically when Ollama is not reachable at localhost:11434,
 * so it never breaks CI or a machine without the model.
 *
 * To run manually:
 *   OLLAMA_MODELS=~/.ollama/models ~/tools/bin/ollama serve &
 *   mvn test -Dtest=OllamaEmbeddingIntegrationTest
 */
@SpringBootTest
class OllamaEmbeddingIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingEngine embeddingEngine;

    @BeforeAll
    static void requireOllama() {
        assumeTrue(isOllamaRunning(), "Ollama not reachable at localhost:11434 — skipping live test");
    }

    // ------------------------------------------------------------------ single document

    @Test
    void embedDocument_returnsVector_ofCorrectDimensions() {
        Chunk c = chunk("doc#0", "Retrieval-Augmented Generation combines retrieval with generation.");
        Embedding e = embeddingEngine.embedDocument(c);

        assertThat(e.vector()).hasSize(768);
        assertThat(e.chunkId()).isEqualTo("doc#0");
        assertThat(e.model()).isEqualTo("nomic-embed-text");
        assertThat(e.inputTokens()).isGreaterThan(0);
    }

    @Test
    void embedDocument_vectorIsUnitNormalised() {
        Chunk c = chunk("doc#1", "The capital of France is Paris.");
        float[] v = embeddingEngine.embedDocument(c).vector();

        double norm = 0;
        for (float f : v) norm += (double) f * f;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(1e-3));
    }

    // ------------------------------------------------------------------ query

    @Test
    void embedQuery_returnsVector_ofCorrectDimensions() {
        Embedding e = embeddingEngine.embedQuery("What is RAG?");

        assertThat(e.vector()).hasSize(768);
        assertThat(e.inputTokens()).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ asymmetry

    @Test
    void queryAndDocumentVectors_areDifferent_forSameText() {
        String text = "Transformer architecture for NLP";
        float[] docVec   = embeddingEngine.embedDocument(chunk("id", text)).vector();
        float[] queryVec = embeddingEngine.embedQuery(text).vector();

        // nomic-embed-text uses different prefixes for query vs document
        assertThat(docVec).isNotEqualTo(queryVec);
    }

    // ------------------------------------------------------------------ semantic similarity

    @Test
    void similarTexts_haveHigherCosineSimilarity_thanUnrelatedTexts() {
        float[] ragDoc   = embeddingEngine.embedDocument(chunk("a", "RAG combines retrieval and generation")).vector();
        float[] ragQuery = embeddingEngine.embedQuery("How does RAG work?").vector();
        float[] unrelated = embeddingEngine.embedQuery("What is the boiling point of water?").vector();

        float simRelated   = cosineSimilarity(ragDoc, ragQuery);
        float simUnrelated = cosineSimilarity(ragDoc, unrelated);

        assertThat(simRelated).isGreaterThan(simUnrelated);
    }

    // ------------------------------------------------------------------ batch

    @Test
    void embedDocuments_returnsOneEmbeddingPerChunk() {
        List<Chunk> chunks = List.of(
                chunk("b#0", "First chunk about machine learning"),
                chunk("b#1", "Second chunk about deep learning"),
                chunk("b#2", "Third chunk about neural networks"));

        List<Embedding> results = embeddingService.embedDocuments(chunks);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(Embedding::chunkId)
                .containsExactly("b#0", "b#1", "b#2");
    }

    // ------------------------------------------------------------------ service layer

    @Test
    void service_embedQuery_blankText_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> embeddingService.embedQuery("  "))
                .withMessageContaining("blank");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isOllamaRunning() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434"))
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() < 500;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already unit-normalised
    }

    private static Chunk chunk(String id, String content) {
        return new Chunk(id, content, new ChunkMetadata("src", 0, 0, content.length()));
    }
}
