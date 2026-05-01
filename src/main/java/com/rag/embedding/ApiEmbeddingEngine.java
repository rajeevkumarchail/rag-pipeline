package com.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.config.EmbeddingProperties;
import com.rag.model.Chunk;
import com.rag.model.Embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding engine backed by any OpenAI-compatible REST API.
 *
 * Compatible with:
 *   OpenAI           https://api.openai.com/v1/embeddings     (text-embedding-3-small)
 *   HuggingFace TEI  https://<endpoint>/embeddings
 *   Ollama           http://localhost:11434/v1/embeddings      (nomic-embed-text)
 *
 * BGE / E5 asymmetry:
 *   Set rag.embedding.document-prefix and rag.embedding.query-prefix in
 *   application.properties.  For symmetric models (OpenAI) leave both empty.
 *
 * Token truncation:
 *   The API response usage.prompt_tokens is recorded in Embedding.inputTokens.
 *   If inputTokens == model's max_tokens the input was silently truncated —
 *   the embedding represents only the first N tokens.
 *
 * Uses java.net.http.HttpClient (Java 11+), so no extra dependency is needed.
 */
public class ApiEmbeddingEngine implements EmbeddingEngine {

    private final EmbeddingProperties props;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ApiEmbeddingEngine(EmbeddingProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    // visible for testing
    ApiEmbeddingEngine(EmbeddingProperties props, ObjectMapper mapper, HttpClient httpClient) {
        this.props = props;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override
    public Embedding embedDocument(Chunk chunk) {
        String text = props.documentPrefix() + chunk.content();
        ApiResponse response = call(List.of(text));
        return new Embedding(
                chunk.id(),
                response.vectors().get(0),
                props.model(),
                response.totalTokens());
    }

    @Override
    public Embedding embedQuery(String text) {
        String prefixed = props.queryPrefix() + text;
        ApiResponse response = call(List.of(prefixed));
        return new Embedding(
                "query:" + text.hashCode(),
                response.vectors().get(0),
                props.model(),
                response.totalTokens());
    }

    @Override
    public List<Embedding> embedDocuments(List<Chunk> chunks) {
        if (chunks.isEmpty()) return List.of();
        List<String> inputs = chunks.stream()
                .map(c -> props.documentPrefix() + c.content())
                .toList();
        ApiResponse response = call(inputs);
        List<Embedding> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            result.add(new Embedding(
                    chunks.get(i).id(),
                    response.vectors().get(i),
                    props.model(),
                    response.totalTokens() / chunks.size())); // distribute evenly
        }
        return result;
    }

    @Override
    public int dimensions() {
        return props.dimensions();
    }

    @Override
    public String modelId() {
        return props.model();
    }

    // ------------------------------------------------------------------ HTTP

    private ApiResponse call(List<String> inputs) {
        try {
            String body = mapper.writeValueAsString(
                    Map.of("model", props.model(), "input", inputs));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.apiUrl()))
                    .header("Authorization", "Bearer " + props.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new EmbeddingException(
                        "Embedding API returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            return parseResponse(response.body());

        } catch (IOException e) {
            throw new EmbeddingException("Embedding API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Embedding API call interrupted", e);
        }
    }

    private ApiResponse parseResponse(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");

        // API returns objects sorted by index — sort defensively just in case
        List<float[]> vectors = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) vectors.add(null); // pre-size
        for (JsonNode item : data) {
            int idx = item.path("index").asInt();
            JsonNode embNode = item.path("embedding");
            float[] vec = new float[embNode.size()];
            for (int j = 0; j < vec.length; j++) {
                vec[j] = (float) embNode.get(j).asDouble();
            }
            vectors.set(idx, vec);
        }

        int totalTokens = root.path("usage").path("total_tokens").asInt(0);
        return new ApiResponse(vectors, totalTokens);
    }

    private record ApiResponse(List<float[]> vectors, int totalTokens) {}
}
