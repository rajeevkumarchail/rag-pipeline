package com.rag.service;

import com.rag.embedding.MockEmbeddingEngine;
import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.model.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EmbeddingServiceTest {

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(new MockEmbeddingEngine(8, "", ""));
    }

    @Test
    void embedDocuments_returnsEmbeddingForEachChunk() {
        List<Chunk> chunks = List.of(chunk("a#0", "Alpha"), chunk("a#1", "Beta"));
        assertThat(service.embedDocuments(chunks)).hasSize(2);
    }

    @Test
    void embedDocuments_nullList_returnsEmpty() {
        assertThat(service.embedDocuments(null)).isEmpty();
    }

    @Test
    void embedDocuments_emptyList_returnsEmpty() {
        assertThat(service.embedDocuments(List.of())).isEmpty();
    }

    @Test
    void embedQuery_returnsEmbedding() {
        Embedding e = service.embedQuery("What is RAG?");
        assertThat(e).isNotNull();
        assertThat(e.vector()).hasSize(8);
    }

    @Test
    void embedQuery_blankText_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.embedQuery("  "))
                .withMessageContaining("blank");
    }

    @Test
    void embedQuery_nullText_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.embedQuery(null));
    }

    private static Chunk chunk(String id, String content) {
        return new Chunk(id, content, new ChunkMetadata("src", 0, 0, content.length()));
    }
}
