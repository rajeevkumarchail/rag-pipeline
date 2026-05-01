package com.rag.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.api.dto.ChunkFromFileRequest;
import com.rag.api.dto.ChunkFromTextRequest;
import com.rag.model.Chunk;
import com.rag.model.ChunkMetadata;
import com.rag.service.ChunkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests only — ChunkingService is mocked.
 * @WebMvcTest spins up just the web slice (no embedded Tomcat, no DB).
 */
@WebMvcTest(ChunkController.class)
class ChunkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChunkingService chunkingService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Chunk SAMPLE = new Chunk(
            "doc#0",
            "Hello world",
            new ChunkMetadata("doc", 0, 0, 11)
    );

    // ------------------------------------------------------------------ /from-text

    @Test
    void fromText_validRequest_returns200WithChunks() throws Exception {
        when(chunkingService.chunkText("Hello world", "doc"))
                .thenReturn(List.of(SAMPLE));

        mockMvc.perform(post("/api/v1/chunks/from-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChunkFromTextRequest("Hello world", "doc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc#0"))
                .andExpect(jsonPath("$[0].content").value("Hello world"))
                .andExpect(jsonPath("$[0].metadata.chunkIndex").value(0));
    }

    @Test
    void fromText_emptyContent_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chunks/from-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "", "sourceId": "doc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void fromText_blankSourceId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chunks/from-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "some text", "sourceId": "  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromText_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chunks/from-text")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromText_serviceReturnsEmpty_returns200WithEmptyArray() throws Exception {
        when(chunkingService.chunkText(any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/chunks/from-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "tiny", "sourceId": "doc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ------------------------------------------------------------------ /from-file

    @Test
    void fromFile_validPath_returns200WithChunks() throws Exception {
        when(chunkingService.chunkFile(any(Path.class))).thenReturn(List.of(SAMPLE));

        mockMvc.perform(post("/api/v1/chunks/from-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filePath": "/tmp/doc.txt"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc#0"));
    }

    @Test
    void fromFile_missingFile_returns404() throws Exception {
        when(chunkingService.chunkFile(any(Path.class)))
                .thenThrow(new FileNotFoundException("File not found: /tmp/missing.txt"));

        mockMvc.perform(post("/api/v1/chunks/from-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filePath": "/tmp/missing.txt"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File not found: /tmp/missing.txt"));
    }

    @Test
    void fromFile_blankPath_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chunks/from-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filePath": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fromFile_ioError_returns500() throws Exception {
        when(chunkingService.chunkFile(any(Path.class)))
                .thenThrow(new IOException("disk read failed"));

        mockMvc.perform(post("/api/v1/chunks/from-file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filePath": "/tmp/corrupt.txt"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to process file: disk read failed"));
    }

    // ------------------------------------------------------------------ /upload

    @Test
    void upload_textFile_returns200WithChunks() throws Exception {
        when(chunkingService.chunkFile(any(Path.class))).thenReturn(List.of(SAMPLE));

        mockMvc.perform(multipart("/api/v1/chunks/upload")
                        .file("file", "Hello world".getBytes()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc#0"));
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/chunks/upload")
                        .file("file", new byte[0]))
                .andExpect(status().isBadRequest());
    }
}
