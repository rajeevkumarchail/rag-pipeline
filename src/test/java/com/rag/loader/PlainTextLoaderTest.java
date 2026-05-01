package com.rag.loader;

import com.rag.model.Chunk;
import com.rag.model.LoaderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PlainTextLoaderTest {

    @TempDir
    Path tempDir;

    private PlainTextLoader loader(int chunkSize, int overlap) {
        return new PlainTextLoader(new LoaderConfig(chunkSize, overlap, 0));
    }

    // ------------------------------------------------------------------ load(Path) — error paths

    @Test
    void null_path_throws_NullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> loader(100, 10).load(null));
    }

    @Test
    void missing_file_throws_FileNotFoundException() {
        Path missing = tempDir.resolve("does_not_exist.txt");
        assertThatExceptionOfType(FileNotFoundException.class)
                .isThrownBy(() -> loader(100, 10).load(missing))
                .withMessageContaining("does_not_exist.txt");
    }

    // ------------------------------------------------------------------ load(Path) — happy path

    @Test
    void empty_file_returns_empty_list() throws IOException {
        Path file = writeFile("empty.txt", "");
        List<Chunk> chunks = loader(100, 10).load(file);
        assertThat(chunks).isEmpty();
    }

    @Test
    void whitespace_only_file_returns_empty_list() throws IOException {
        Path file = writeFile("blank.txt", "   \n\n  ");
        assertThat(loader(100, 10).load(file)).isEmpty();
    }

    @Test
    void short_file_produces_single_chunk() throws IOException {
        String content = "Hello, world!";
        Path file = writeFile("short.txt", content);
        List<Chunk> chunks = loader(100, 10).load(file);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(content);
    }

    @Test
    void long_file_produces_multiple_chunks() throws IOException {
        String sentence = "This is a sentence that has enough words to be meaningful. ";
        String content = sentence.repeat(20);
        Path file = writeFile("long.txt", content);

        List<Chunk> chunks = loader(100, 15).load(file);

        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void chunks_are_sequentially_indexed() throws IOException {
        String content = "Para one.\n\nPara two.\n\nPara three.\n\nPara four.\n\nPara five.";
        Path file = writeFile("paras.txt", content);

        List<Chunk> chunks = loader(30, 5).load(file);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).metadata().chunkIndex())
                    .as("chunk at position %d has index %d", i, i)
                    .isEqualTo(i);
        }
    }

    @Test
    void chunk_ids_are_unique() throws IOException {
        String content = "Word ".repeat(200);
        Path file = writeFile("ids.txt", content);

        List<Chunk> chunks = loader(50, 10).load(file);

        assertThat(chunks).extracting(Chunk::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void chunk_id_encodes_source_and_index() throws IOException {
        Path file = writeFile("source.txt", "Some content here.");
        List<Chunk> chunks = loader(100, 0).load(file);

        assertThat(chunks.get(0).id())
                .contains(file.toString())
                .contains("#0");
    }

    @Test
    void chunk_metadata_sourceId_matches_file_path() throws IOException {
        Path file = writeFile("meta.txt", "Some content here.");
        List<Chunk> chunks = loader(100, 0).load(file);

        assertThat(chunks.get(0).metadata().sourceId())
                .isEqualTo(file.toString());
    }

    @Test
    void startChar_and_endChar_are_consistent() throws IOException {
        String content = "Hello world this is a test sentence for offset tracking.";
        Path file = writeFile("offsets.txt", content);

        List<Chunk> chunks = loader(100, 0).load(file);

        for (Chunk chunk : chunks) {
            int start = chunk.metadata().startChar();
            int end = chunk.metadata().endChar();
            assertThat(end - start).isEqualTo(chunk.content().length());
        }
    }

    @Test
    void startChar_metadata_has_correct_structural_properties() throws IOException {
        // Overlap re-seeding strips separators, so chunk content may not appear
        // verbatim in the original source. What we can assert is ordering and bounds.
        String content = "First part of text.\n\nSecond part of text.\n\nThird part of text.";
        Path file = writeFile("position.txt", content);

        List<Chunk> chunks = loader(30, 5).load(file);

        // startChars must be strictly increasing (chunks are ordered)
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).metadata().startChar())
                    .as("startChar of chunk %d > startChar of chunk %d", i, i - 1)
                    .isGreaterThan(chunks.get(i - 1).metadata().startChar());
        }

        // All offsets must be within source bounds
        for (Chunk chunk : chunks) {
            assertThat(chunk.metadata().startChar()).isBetween(0, content.length());
            assertThat(chunk.metadata().endChar()).isBetween(0, content.length());
        }
    }

    @Test
    void no_chunk_exceeds_chunkSize_plus_overlap() throws IOException {
        // chunkSize is a soft target; overlap re-seeding can add up to chunkOverlap more.
        String content = "Sentence number one is here. ".repeat(50);
        Path file = writeFile("sizecheck.txt", content);
        LoaderConfig cfg = new LoaderConfig(100, 20, 0);

        List<Chunk> chunks = new PlainTextLoader(cfg).load(file);

        chunks.forEach(c ->
                assertThat(c.content().length())
                        .as("chunk fits within chunkSize + chunkOverlap soft limit")
                        .isLessThanOrEqualTo(cfg.chunkSize() + cfg.chunkOverlap()));
    }

    // ------------------------------------------------------------------ loadFromString

    @Test
    void loadFromString_null_content_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> loader(100, 10).loadFromString(null, "src"));
    }

    @Test
    void loadFromString_null_sourceId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> loader(100, 10).loadFromString("text", null));
    }

    @Test
    void loadFromString_blank_sourceId_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> loader(100, 10).loadFromString("text", "  "))
                .withMessageContaining("sourceId");
    }

    @Test
    void loadFromString_empty_content_returns_empty_list() {
        assertThat(loader(100, 10).loadFromString("", "src")).isEmpty();
    }

    @Test
    void loadFromString_sets_correct_sourceId() {
        List<Chunk> chunks = loader(100, 0).loadFromString("Hello world.", "my-source");
        assertThat(chunks.get(0).metadata().sourceId()).isEqualTo("my-source");
    }

    @Test
    void loadFromString_produces_same_content_as_load() throws IOException {
        String content = "Para A.\n\nPara B.\n\nPara C.\n\nPara D.\n\nPara E.";
        Path file = writeFile("compare.txt", content);
        PlainTextLoader l = loader(20, 3);

        List<Chunk> fromFile = l.load(file);
        List<Chunk> fromString = l.loadFromString(content, file.toString());

        assertThat(fromFile).extracting(Chunk::content)
                .isEqualTo(fromString.stream().map(Chunk::content).toList());
    }

    // ------------------------------------------------------------------ utility

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
