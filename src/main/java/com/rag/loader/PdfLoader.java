package com.rag.loader;

import com.rag.model.Chunk;
import com.rag.model.LoaderConfig;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Extracts text from PDF files using PDFBox, then delegates to PlainTextLoader.
 *
 * PDFBox's PDFTextStripper preserves paragraph breaks as \n\n, which lets
 * RecursiveTextSplitter prefer semantic splits over hard character cuts.
 */
public class PdfLoader implements DocumentLoader {

    private final PlainTextLoader delegate;

    public PdfLoader(LoaderConfig config) {
        this.delegate = new PlainTextLoader(
                Objects.requireNonNull(config, "config must not be null"));
    }

    @Override
    public List<Chunk> load(Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath must not be null");
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        String text = extractText(filePath);
        return delegate.loadFromString(text, filePath.toString());
    }

    @Override
    public List<Chunk> loadFromString(String content, String sourceId) {
        return delegate.loadFromString(content, sourceId);
    }

    private String extractText(Path filePath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }
}
