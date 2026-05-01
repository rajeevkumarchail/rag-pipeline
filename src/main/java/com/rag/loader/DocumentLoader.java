package com.rag.loader;

import com.rag.model.Chunk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Contract for anything that turns a source document into an ordered list of Chunks.
 *
 * Implementations are responsible for both text extraction and chunking.
 * The split strategy is injected via RecursiveTextSplitter so callers can
 * tune chunkSize / overlap without subclassing the loader.
 */
public interface DocumentLoader {

    /** Load and chunk a file from disk. */
    List<Chunk> load(Path filePath) throws IOException;

    /** Load and chunk an in-memory string (useful for testing and streaming ingestion). */
    List<Chunk> loadFromString(String content, String sourceId);
}
