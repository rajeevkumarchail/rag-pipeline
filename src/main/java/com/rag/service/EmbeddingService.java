package com.rag.service;

import com.rag.embedding.EmbeddingEngine;
import com.rag.model.Chunk;
import com.rag.model.Embedding;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingEngine engine;

    public EmbeddingService(EmbeddingEngine engine) {
        this.engine = engine;
    }

    public List<Embedding> embedDocuments(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        return engine.embedDocuments(chunks);
    }

    public Embedding embedQuery(String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("query text must not be blank");
        return engine.embedQuery(text);
    }
}
