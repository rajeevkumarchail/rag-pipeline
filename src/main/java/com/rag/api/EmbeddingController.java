package com.rag.api;

import com.rag.api.dto.EmbedQueryRequest;
import com.rag.model.Chunk;
import com.rag.model.Embedding;
import com.rag.service.EmbeddingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * POST /api/v1/embeddings/documents  body: [ Chunk, ... ]
 * POST /api/v1/embeddings/query      body: { "text": "..." }
 */
@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/documents")
    public ResponseEntity<List<Embedding>> embedDocuments(@RequestBody List<Chunk> chunks) {
        return ResponseEntity.ok(embeddingService.embedDocuments(chunks));
    }

    @PostMapping("/query")
    public ResponseEntity<Embedding> embedQuery(@RequestBody EmbedQueryRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return ResponseEntity.ok(embeddingService.embedQuery(request.text()));
    }
}
