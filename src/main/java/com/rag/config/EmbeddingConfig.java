package com.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.embedding.ApiEmbeddingEngine;
import com.rag.embedding.EmbeddingEngine;
import com.rag.embedding.MockEmbeddingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingEngine embeddingEngine(EmbeddingProperties props, ObjectMapper mapper) {
        return switch (props.provider()) {
            case "api"  -> new ApiEmbeddingEngine(props, mapper);
            case "mock" -> new MockEmbeddingEngine(
                                props.dimensions(), props.queryPrefix(), props.documentPrefix());
            default -> throw new IllegalArgumentException(
                                "Unknown embedding provider: " + props.provider()
                                + " — use 'api' or 'mock'");
        };
    }
}
