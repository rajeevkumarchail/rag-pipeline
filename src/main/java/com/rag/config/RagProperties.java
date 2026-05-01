package com.rag.config;

import com.rag.model.LoaderConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds application.properties prefix "rag.loader" to a LoaderConfig.
 *
 * Spring Boot maps kebab-case property names to camelCase record components:
 *   rag.loader.chunk-size    → chunkSize
 *   rag.loader.chunk-overlap → chunkOverlap
 *   rag.loader.min-chunk-size → minChunkSize
 *
 * LoaderConfig's compact constructor validates the values at startup,
 * so a misconfigured application fails fast before accepting any requests.
 */
@ConfigurationProperties(prefix = "rag.loader")
public record RagProperties(int chunkSize, int chunkOverlap, int minChunkSize) {

    public LoaderConfig toLoaderConfig() {
        return new LoaderConfig(chunkSize, chunkOverlap, minChunkSize);
    }
}
