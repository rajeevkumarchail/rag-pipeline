package com.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.api.dto.EmbedQueryRequest;
import com.rag.embedding.EmbeddingException;
import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.model.Embedding;
import com.rag.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmbeddingController.class)
class EmbeddingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  EmbeddingService embeddingService;

    private static final Chunk CHUNK = new Chunk(
            "doc#0", "Hello world", new ChunkMetadata("doc", 0, 0, 11));

    private static final Embedding EMBEDDING = new Embedding(
            "doc#0", new float[]{0.1f, 0.2f, 0.3f}, "mock-3d", 3);

    // ------------------------------------------------------------------ /documents

    @Test
    void embedDocuments_returnsOkWithEmbeddings() throws Exception {
        when(embeddingService.embedDocuments(anyList())).thenReturn(List.of(EMBEDDING));

        mockMvc.perform(post("/api/v1/embeddings/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(List.of(CHUNK))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkId").value("doc#0"))
                .andExpect(jsonPath("$[0].model").value("mock-3d"))
                .andExpect(jsonPath("$[0].vector").isArray());
    }

    @Test
    void embedDocuments_emptyList_returns200WithEmptyArray() throws Exception {
        when(embeddingService.embedDocuments(anyList())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/embeddings/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void embedDocuments_engineThrows_returns502() throws Exception {
        when(embeddingService.embedDocuments(anyList()))
                .thenThrow(new EmbeddingException("API returned HTTP 429: rate limit"));

        mockMvc.perform(post("/api/v1/embeddings/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(List.of(CHUNK))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("API returned HTTP 429: rate limit"));
    }

    // ------------------------------------------------------------------ /query

    @Test
    void embedQuery_returnsOkWithEmbedding() throws Exception {
        Embedding queryEmbedding = new Embedding("query:123", new float[]{0.5f}, "mock-1d", 4);
        when(embeddingService.embedQuery("What is RAG?")).thenReturn(queryEmbedding);

        mockMvc.perform(post("/api/v1/embeddings/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new EmbedQueryRequest("What is RAG?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkId").value("query:123"))
                .andExpect(jsonPath("$.vector").isArray());
    }

    @Test
    void embedQuery_blankText_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/embeddings/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void embedQuery_serviceThrows_returns502() throws Exception {
        when(embeddingService.embedQuery(any()))
                .thenThrow(new EmbeddingException("model overloaded"));

        mockMvc.perform(post("/api/v1/embeddings/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "some query"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("model overloaded"));
    }
}
