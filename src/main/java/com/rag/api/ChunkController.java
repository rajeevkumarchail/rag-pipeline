package com.rag.api;

import com.rag.api.dto.ChunkFromFileRequest;
import com.rag.api.dto.ChunkFromTextRequest;
import com.rag.model.Chunk;
import com.rag.service.ChunkingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * POST /api/v1/chunks/upload      multipart/form-data  field: file
 * POST /api/v1/chunks/from-file   { "filePath": "/absolute/path/to/doc.txt" }
 * POST /api/v1/chunks/from-text   { "content": "...", "sourceId": "my-doc" }
 *
 * All three return: [ { "id": "...", "content": "...", "metadata": { ... } } ]
 *
 * Error mapping is in GlobalExceptionHandler — the controller stays exception-free.
 */
@RestController
@RequestMapping("/api/v1/chunks")
public class ChunkController {

    private final ChunkingService chunkingService;

    public ChunkController(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<Chunk>> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        Path tmp = Files.createTempFile("rag-", "-" + originalName);
        try {
            file.transferTo(tmp);
            return ResponseEntity.ok(chunkingService.chunkFile(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @PostMapping("/from-file")
    public ResponseEntity<List<Chunk>> fromFile(@RequestBody ChunkFromFileRequest request)
            throws IOException {
        if (request.filePath() == null || request.filePath().isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        return ResponseEntity.ok(chunkingService.chunkFile(Path.of(request.filePath())));
    }

    @PostMapping("/from-text")
    public ResponseEntity<List<Chunk>> fromText(@RequestBody ChunkFromTextRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (request.sourceId() == null || request.sourceId().isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        return ResponseEntity.ok(chunkingService.chunkText(request.content(), request.sourceId()));
    }
}
