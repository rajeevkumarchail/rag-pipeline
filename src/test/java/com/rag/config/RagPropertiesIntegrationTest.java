package com.rag.config;

import com.rag.service.ChunkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the full wiring chain:
 *
 *   application.properties
 *     → RagProperties  (via @ConfigurationPropertiesScan)
 *       → RagConfig    (creates PlainTextLoader / PdfLoader beans)
 *         → ChunkingService
 *           → actual chunking behaviour
 */
@SpringBootTest
class RagPropertiesIntegrationTest {

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private ChunkingService chunkingService;

    // ------------------------------------------------------------------ property binding

    @Test
    void chunkSize_isLoadedFromApplicationProperties() {
        assertThat(ragProperties.chunkSize()).isEqualTo(500);
    }

    @Test
    void chunkOverlap_isLoadedFromApplicationProperties() {
        assertThat(ragProperties.chunkOverlap()).isEqualTo(50);
    }

    @Test
    void minChunkSize_isLoadedFromApplicationProperties() {
        assertThat(ragProperties.minChunkSize()).isEqualTo(20);
    }

    // ------------------------------------------------------------------ end-to-end effect

    @Test
    void shortText_producesOneChunk_becauseItFitsWithinConfiguredChunkSize() {
        // Text is well under 500 chars → should produce exactly 1 chunk
        String text = "The quick brown fox jumps over the lazy dog.";
        var chunks = chunkingService.chunkText(text, "test");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(text);
    }

    @Test
    void longText_producesMultipleChunks_withOverlapBetweenThem() {
        // Build text clearly longer than chunk-size=500
        String sentence = "This sentence is used to build a document that exceeds the configured chunk size. ";
        String text = sentence.repeat(12); // ~960 chars

        var chunks = chunkingService.chunkText(text, "test");

        assertThat(chunks).hasSizeGreaterThan(1);

        // Verify consecutive chunks share overlap content (configured overlap = 50)
        for (int i = 0; i < chunks.size() - 1; i++) {
            String tail = chunks.get(i).content();
            tail = tail.substring(Math.max(0, tail.length() - ragProperties.chunkOverlap()));
            assertThat(chunks.get(i + 1).content())
                    .as("chunk %d and chunk %d should share overlap", i, i + 1)
                    .contains(tail.strip());
        }
    }

    @Test
    void noChunk_exceedsConfiguredSoftLimit() {
        String text = "Word number one two three four five. ".repeat(50);
        var chunks = chunkingService.chunkText(text, "test");
        int softLimit = ragProperties.chunkSize() + ragProperties.chunkOverlap();
        chunks.forEach(c ->
                assertThat(c.content().length())
                        .as("chunk stays within chunkSize + chunkOverlap = %d", softLimit)
                        .isLessThanOrEqualTo(softLimit));
    }
}
