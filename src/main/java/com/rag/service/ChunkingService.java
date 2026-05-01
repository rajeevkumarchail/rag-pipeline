package com.rag.service;

import com.rag.loader.PdfLoader;
import com.rag.loader.PlainTextLoader;
import com.rag.model.Chunk;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Routes chunk requests to the right loader based on file extension.
 * This is the only call-site that knows about the loader implementations —
 * the controller only depends on this service.
 */
@Service
public class ChunkingService {

    private final PlainTextLoader plainTextLoader;
    private final PdfLoader pdfLoader;

    public ChunkingService(PlainTextLoader plainTextLoader, PdfLoader pdfLoader) {
        this.plainTextLoader = plainTextLoader;
        this.pdfLoader = pdfLoader;
    }

    public List<Chunk> chunkFile(Path filePath) throws IOException {
        if (filePath.toString().toLowerCase().endsWith(".pdf")) {
            return pdfLoader.load(filePath);
        }
        return plainTextLoader.load(filePath);
    }

    public List<Chunk> chunkText(String content, String sourceId) {
        return plainTextLoader.loadFromString(content, sourceId);
    }
}
