package com.rag.loader;

import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.model.LoaderConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads plain-text files (.txt, .md, etc.) and produces Chunks with source offsets.
 *
 * Character offset tracking:
 *   After obtaining the ordered chunk texts from the splitter, we search for each
 *   chunk in the original string starting from where the *previous* chunk ended
 *   minus chunkOverlap. This is accurate for all non-duplicate content; for
 *   documents that repeat the same phrase verbatim we'd get the first match, which
 *   is an acceptable approximation for retrieval use cases.
 */
public class PlainTextLoader implements DocumentLoader {

    private final RecursiveTextSplitter splitter;

    public PlainTextLoader(LoaderConfig config) {
        this.splitter = new RecursiveTextSplitter(
                Objects.requireNonNull(config, "config must not be null"));
    }

    @Override
    public List<Chunk> load(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return toChunks(splitter.split(content), content, filePath.toString());
    }

    @Override
    public List<Chunk> loadFromString(String content, String sourceId) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (sourceId.isBlank())
            throw new IllegalArgumentException("sourceId must not be blank");
        return toChunks(splitter.split(content), content, sourceId);
    }

    private List<Chunk> toChunks(List<String> texts, String original, String sourceId) {
        List<Chunk> chunks = new ArrayList<>(texts.size());
        int searchFrom = 0;

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            int start = original.indexOf(text, searchFrom);
            if (start < 0) start = searchFrom; // fallback: duplicate text edge case
            int end = start + text.length();

            ChunkMetadata meta = new ChunkMetadata(sourceId, i, start, end);
            chunks.add(new Chunk(sourceId + "#" + i, text, meta));

            // Advance search cursor: end minus overlap so the next indexOf finds the
            // overlapping chunk at its actual position in the source.
            searchFrom = Math.max(0, end - splitter.getConfig().chunkOverlap());
        }

        return chunks;
    }
}
