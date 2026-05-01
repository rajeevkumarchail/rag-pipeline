package com.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds application.properties prefix "rag.embedding".
 *
 * provider         — "api" (calls a remote REST endpoint) or "mock" (no network, for dev/test)
 * model            — exact model identifier sent to the API and written into every Embedding
 * apiUrl           — OpenAI-compatible embeddings endpoint
 * apiKey           — Bearer token; set via env var EMBEDDING_API_KEY to avoid committing secrets
 * dimensions       — output vector size; must match the model (OpenAI small=1536, BGE-base=768)
 * queryPrefix      — prepended to query text; "" for symmetric models, "query: " for E5
 * documentPrefix   — prepended to document text; "" for most models, "passage: " for E5
 */
@ConfigurationProperties(prefix = "rag.embedding")
public record EmbeddingProperties(
        String provider,
        String model,
        String apiUrl,
        String apiKey,
        int dimensions,
        String queryPrefix,
        String documentPrefix
) {}
