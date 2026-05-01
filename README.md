# RAG Pipeline

A hand-rolled Retrieval-Augmented Generation pipeline in Java 17 / Spring Boot 3.
No LangChain, no LlamaIndex — every component is implemented from scratch.

## Prerequisites

| Tool | Version | Path on this machine |
|------|---------|----------------------|
| JDK  | 17      | `~/.jdks/jdk-17.0.2` |
| Maven | 3.9.6  | `~/tools/apache-maven-3.9.6` |

Add to `~/.bashrc` (already done):
```bash
export JAVA_HOME="$HOME/.jdks/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Build & test

```bash
# Run all 54 tests
mvn test

# Build executable fat JAR
mvn package -DskipTests
```

## Run the server

```bash
mvn spring-boot:run
# or
java -jar target/rag-pipeline-1.0-SNAPSHOT.jar
```

Server starts on **http://localhost:8080**

## Browser UI

Open **http://localhost:8080** — a page that lets you:
- Upload a `.txt`, `.md`, or `.pdf` file and see its chunks
- Paste inline text and chunk it immediately
- Click any chunk to expand and read its content

## API endpoints

### Upload a file  ← easiest way to try it
```
POST /api/v1/chunks/upload
Content-Type: multipart/form-data
field: file
```
```bash
curl -s -X POST http://localhost:8080/api/v1/chunks/upload \
  -F "file=@/path/to/document.txt" | jq .
```

### Chunk text already on the server
```
POST /api/v1/chunks/from-file
Content-Type: application/json
{ "filePath": "/absolute/path/on/server.txt" }
```
```bash
curl -s -X POST http://localhost:8080/api/v1/chunks/from-file \
  -H 'Content-Type: application/json' \
  -d '{"filePath": "/tmp/my-doc.txt"}' | jq .
```

### Chunk inline text
```
POST /api/v1/chunks/from-text
Content-Type: application/json
{ "content": "...", "sourceId": "my-doc" }
```
```bash
curl -s -X POST http://localhost:8080/api/v1/chunks/from-text \
  -H 'Content-Type: application/json' \
  -d '{"content": "The quick brown fox...", "sourceId": "test"}' | jq .
```

### Health check
```bash
curl http://localhost:8080/actuator/health
```

### Response shape
```json
[
  {
    "id": "my-doc#0",
    "content": "The quick brown fox...",
    "metadata": {
      "sourceId": "my-doc",
      "chunkIndex": 0,
      "startChar": 0,
      "endChar": 487
    }
  }
]
```

### Error responses
| Situation | HTTP status |
|-----------|-------------|
| File not found | 404 |
| Blank / missing field | 400 |
| Disk / parse error | 500 |

## Configuration

Edit `src/main/resources/application.properties`:

```properties
rag.loader.chunk-size=500       # target characters per chunk
rag.loader.chunk-overlap=50     # characters shared between consecutive chunks
rag.loader.min-chunk-size=20    # discard chunks shorter than this

spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
```

`chunkSize` is a soft target: overlap re-seeding can produce chunks up to
`chunkSize + chunkOverlap` characters. This matches LangChain's behaviour —
the overlap guarantee takes priority over the strict size cap.

## Project layout

```
src/main/java/com/rag/
  RagPipelineApplication.java     Spring Boot entry point
  config/
    RagProperties.java            @ConfigurationProperties — binds application.properties
    RagConfig.java                @Configuration — wires loader beans
  model/
    LoaderConfig.java             Immutable config record (validates at construction time)
    Chunk.java                    Single text segment ready for embedding
    ChunkMetadata.java            Provenance: source, index, char offsets
  loader/
    DocumentLoader.java           Interface: load(Path) + loadFromString(String, String)
    RecursiveTextSplitter.java    Two-phase split: paragraph → line → sentence → word → char
    PlainTextLoader.java          Loads .txt / .md, tracks char offsets
    PdfLoader.java                PDFBox 3.x extraction, delegates to PlainTextLoader
  service/
    ChunkingService.java          Routes .pdf vs plaintext, single call-site
  api/
    ChunkController.java          REST endpoints
    GlobalExceptionHandler.java   Maps exceptions to HTTP status codes
    dto/                          Thin request records

src/test/java/com/rag/
  model/LoaderConfigTest.java           10 tests
  loader/RecursiveTextSplitterTest.java 14 tests
  loader/PlainTextLoaderTest.java       19 tests
  api/ChunkControllerTest.java          11 tests  (@WebMvcTest, mocked service)
```

## Components still to build

| # | Component | Notes |
|---|-----------|-------|
| 2 | Embedding Engine | DJL or Anthropic API |
| 3 | Vector Store | FAISS or pure-Java ANN |
| 4 | Retriever | top-k + MMR re-ranking |
| 5 | Generator | prompt construction + LLM call + citations |
# rag-pipeline
