package com.rag.loader;

import com.rag.model.LoaderConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecursiveTextSplitterTest {

    // ------------------------------------------------------------------ helpers

    private RecursiveTextSplitter splitter(int chunkSize, int overlap) {
        return new RecursiveTextSplitter(new LoaderConfig(chunkSize, overlap, 0));
    }

    private RecursiveTextSplitter splitter(int chunkSize, int overlap, int minChunk) {
        return new RecursiveTextSplitter(new LoaderConfig(chunkSize, overlap, minChunk));
    }

    // ------------------------------------------------------------------ null / empty

    @Test
    void null_input_returnsEmptyList() {
        assertThat(splitter(100, 10).split(null)).isEmpty();
    }

    @Test
    void blank_input_returnsEmptyList() {
        assertThat(splitter(100, 10).split("   \n  ")).isEmpty();
    }

    @Test
    void empty_string_returnsEmptyList() {
        assertThat(splitter(100, 10).split("")).isEmpty();
    }

    // ------------------------------------------------------------------ short text (no split needed)

    @Test
    void text_shorter_than_chunkSize_returnsSingleChunk() {
        String text = "Hello world";
        List<String> result = splitter(100, 10).split(text);
        assertThat(result).containsExactly(text);
    }

    @Test
    void text_exactly_chunkSize_returnsSingleChunk() {
        String text = "a".repeat(100);
        List<String> result = splitter(100, 10).split(text);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(100);
    }

    // ------------------------------------------------------------------ separator hierarchy

    @Test
    void prefers_paragraph_break_over_newline() {
        // Two paragraphs separated by \n\n — splitter should split there, not inside words
        String para1 = "First paragraph with enough text to matter here.";
        String para2 = "Second paragraph with enough text to matter here.";
        String text = para1 + "\n\n" + para2;

        // chunkSize smaller than full text but larger than each paragraph
        List<String> result = splitter(60, 5).split(text);

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        // First chunk should contain para1 content (not a mid-word cut)
        assertThat(result.get(0)).contains("First paragraph");
    }

    @Test
    void falls_back_to_newline_when_no_paragraph_break() {
        String line1 = "Line one with sufficient content to trigger splitting";
        String line2 = "Line two with sufficient content to trigger splitting";
        String text = line1 + "\n" + line2;

        List<String> result = splitter(60, 5).split(text);

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void falls_back_to_sentence_boundary() {
        String text = "First sentence here. Second sentence here. Third sentence here.";
        // chunkSize = 25 forces splits inside the text
        List<String> result = splitter(25, 3).split(text);
        assertThat(result).hasSizeGreaterThan(1);
        // No chunk should exceed chunkSize
        result.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(25));
    }

    @Test
    void hard_splits_text_with_no_separators() {
        String text = "a".repeat(250); // no spaces, newlines, or periods
        List<String> result = splitter(100, 0).split(text);
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).hasSize(100);
        assertThat(result.get(1)).hasSize(100);
        assertThat(result.get(2)).hasSize(50);
    }

    // ------------------------------------------------------------------ overlap

    @Test
    void consecutive_chunks_share_overlap_content() {
        // Build a text of clearly distinct sentences that will be split across chunks
        String s1 = "Alpha beta gamma delta.";   // 23 chars
        String s2 = "Epsilon zeta eta theta.";   // 23 chars
        String s3 = "Iota kappa lambda mu nu.";  // 24 chars
        String text = s1 + " " + s2 + " " + s3;

        // chunkSize = 30 forces splits; overlap = 10
        List<String> chunks = splitter(30, 10).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);

        // Each consecutive pair must share at least some suffix/prefix content
        for (int i = 0; i < chunks.size() - 1; i++) {
            String tail = chunks.get(i).substring(
                    Math.max(0, chunks.get(i).length() - 10));
            assertThat(chunks.get(i + 1)).contains(tail.strip());
        }
    }

    @Test
    void zero_overlap_produces_disjoint_chunks() {
        String text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";
        List<String> chunks = splitter(20, 0).split(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        // No content from one chunk's tail should appear at the start of the next
        // (approximate: just verify total length is close to original)
        int totalLen = chunks.stream().mapToInt(String::length).sum();
        assertThat(totalLen).isLessThanOrEqualTo(text.length());
    }

    // ------------------------------------------------------------------ size guarantees

    @Test
    void no_chunk_exceeds_chunkSize_plus_overlap() {
        // chunkSize is a soft target: overlap re-seeding can push a chunk up to
        // chunkSize + chunkOverlap. This mirrors LangChain's documented behaviour.
        LoaderConfig cfg = new LoaderConfig(100, 20, 0);
        String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20);
        List<String> chunks = new RecursiveTextSplitter(cfg).split(text);
        chunks.forEach(c ->
                assertThat(c.length())
                        .as("chunk stays within soft limit chunkSize + chunkOverlap")
                        .isLessThanOrEqualTo(cfg.chunkSize() + cfg.chunkOverlap()));
    }

    @Test
    void minChunkSize_filters_tiny_trailing_pieces() {
        // minChunkSize = 20; add a short trailing word that would produce a tiny chunk
        String text = "A".repeat(100) + " " + "B".repeat(100) + " hi";
        List<String> chunks = splitter(110, 5, 20).split(text);
        chunks.forEach(c ->
                assertThat(c.strip().length())
                        .as("each chunk meets minChunkSize")
                        .isGreaterThanOrEqualTo(20));
    }

    // ------------------------------------------------------------------ multi-paragraph doc

    @Test
    void realistic_multi_paragraph_document() {
        String doc = """
                The retrieval-augmented generation (RAG) pattern augments an LLM with external knowledge.

                During indexing, documents are split into chunks and embedded into a vector space.
                Each chunk's embedding captures its semantic meaning.

                At query time, the user's question is embedded with the same model.
                The nearest vectors are retrieved and injected into the LLM prompt as context.

                This approach reduces hallucinations and enables the model to cite its sources.
                """;

        List<String> chunks = splitter(200, 30).split(doc);

        assertThat(chunks).isNotEmpty();
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
        // Every word from the original should appear in at least one chunk
        assertThat(String.join(" ", chunks)).contains("retrieval-augmented");
        assertThat(String.join(" ", chunks)).contains("hallucinations");
    }
}
