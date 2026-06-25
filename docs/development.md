# Development Guide

last_updated: 2026-06-25

## Prerequisites

- **JDK 17** minimum (Spring Boot 3 minimum requirement). JDK 21 also works — change `<java.version>` in `pom.xml`.
- **Docker + Docker Compose** — runs PostgreSQL 16 and ChromaDB 1.0.0 locally.
- **IDE** — IntelliJ IDEA recommended (Spring Boot run configuration, JPA entity diagrams). VS Code with the Spring Boot Extension Pack also works.
- **OpenAI API key** for the default profile (or a Gemini / Ollama alternative — see [Adding a new LLM provider](#adding-a-new-llm-provider)).

## Local setup

```bash
# 1. Clone
git clone <repo-url>
cd spring-ai-features

# 2. Start dependencies (PostgreSQL + ChromaDB)
docker compose up -d

# 3. Verify containers are healthy
docker compose ps

# 4. Set environment variables (minimum for default OpenAI profile)
export OPENAI_API_KEY=sk-...
# Optional: override DB password (default is 'rag')
export DB_PASSWORD=rag

# 5. Run
./mvnw spring-boot:run
```

The application starts at `http://localhost:8080`. Hibernate creates the `documents` and
`document_chunks` tables automatically on first start (`ddl-auto: update`).

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `OPENAI_API_KEY` | Yes (default profile) | — | OpenAI API key for chat and embeddings |
| `GEMINI_API_KEY` | Yes (gemini profile) | — | Google AI Studio key; used as `api-key` in the OpenAI-compatible endpoint |
| `DB_PASSWORD` | No | `rag` | PostgreSQL password for the `rag` user; must match `POSTGRES_PASSWORD` |
| `POSTGRES_PASSWORD` | No | `rag` | PostgreSQL superuser password set in docker-compose.yml; must match `DB_PASSWORD` |
| `SPRING_PROFILES_ACTIVE` | No | *(none, OpenAI)* | Set to `ollama` or `gemini` to switch providers |

When `DB_PASSWORD` and `POSTGRES_PASSWORD` diverge, the app will fail to connect to the database.

## Running tests

```bash
./mvnw test
```

No running database, AI provider, or ChromaDB instance is needed — all external dependencies
are mocked with Mockito or `@WebMvcTest` MockMvc.

| Test class | Type | What it covers |
|---|---|---|
| `RagServiceTest` | Unit (Mockito) | RAG flow: search returns empty list, successful answer, null content from model |
| `DocumentServiceTest` | Unit (Mockito) | Upload validation, indexing, deletion, reindex, stats |
| `SummaryServiceTest` | Unit (Mockito) | Summarization happy path, missing text guard, AI exception mapping |
| `RestApiIntegrationTest` | `@WebMvcTest` + MockMvc | HTTP contract — status codes, request validation, error response format |

## Project layout

```
src/main/java/com/example/rag/
├── config/
│   └── ChatClientConfig.java         # ChatClient bean
├── controller/
│   ├── DocumentController.java       # /api/documents/**
│   └── ChatController.java           # /api/chat/ask
├── service/
│   ├── DocumentService.java          # document lifecycle orchestration
│   ├── PdfProcessingService.java     # PDF read + chunk (PDFBox + TokenTextSplitter)
│   ├── RagService.java               # retrieve → augment → generate
│   └── SummaryService.java           # LLM summarization with structured output
├── vectorstore/
│   └── ChromaService.java            # VectorStore facade (add / search / delete)
├── embedding/
│   └── EmbeddingService.java         # exposes EmbeddingModel.dimensions()
├── rag/
│   └── PromptTemplates.java          # RAG_SYSTEM, RAG_USER, SUMMARY_SYSTEM, SUMMARY_USER
├── entity/
│   ├── Document.java                 # JPA entity, documents table
│   ├── DocumentChunk.java            # JPA entity, document_chunks table
│   └── DocumentStatus.java           # UPLOADED / PROCESSING / INDEXED / FAILED
├── repository/
│   ├── DocumentRepository.java       # JpaRepository<Document, Long>
│   └── DocumentChunkRepository.java  # JpaRepository<DocumentChunk, Long>
├── dto/
│   ├── AskRequest.java / AskResponse.java
│   ├── UploadResponse.java / DocumentResponse.java
│   ├── SummaryResponse.java / StatsResponse.java
│   └── ApiError.java
└── exception/
    ├── GlobalExceptionHandler.java   # @RestControllerAdvice
    ├── DocumentNotFoundException.java
    ├── InvalidFileException.java
    ├── PdfProcessingException.java
    └── AiServiceException.java
```

## Adding a new LLM provider

Follow these steps to add a provider that Spring AI supports (e.g. Anthropic, Mistral).

**1. Add the starter dependency to `pom.xml`.**
Mark it `<optional>true</optional>` to prevent `NoUniqueBeanDefinitionException` when the
profile is not active.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
    <optional>true</optional>
</dependency>
```

**2. Create `src/main/resources/application-{profile}.yml`.**
Override the model provider selection and provider-specific settings.

```yaml
spring:
  ai:
    model:
      chat: anthropic
      embedding: anthropic       # or use openai embeddings if the provider has no embedding model
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-5-sonnet-20241022
          temperature: 0.2
```

**3. Activate the profile at runtime.**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
SPRING_PROFILES_ACTIVE=anthropic ./mvnw spring-boot:run
```

**4. Reindex all documents** if the new provider uses a different embedding model (see
[Re-indexing after model switch](#re-indexing-after-model-switch)).

No application code needs to change — `ChatClient` and `VectorStore` are injected as
abstractions and work with any configured provider.

## Adding a new endpoint

**1. Define a DTO** (record or class) in the `dto` package for the request and/or response.

**2. Add the handler to the appropriate controller** (or create a new `@RestController` if the
resource is distinct). Use `@Valid` on request bodies and return `ResponseEntity<T>` when you
need a non-200 status.

**3. Implement the business logic in a `@Service` class.** If the operation reads or writes
PostgreSQL, inject the relevant `JpaRepository`. If it reads or writes ChromaDB, call
`ChromaService`. If it calls the LLM, inject `ChatClient`.

**4. Define a domain exception** in the `exception` package if the new operation can fail
in a way that maps to a specific HTTP status.

**5. Add a case to `GlobalExceptionHandler`** mapping the new exception to the correct
`HttpStatus`.

Example: adding `GET /api/documents/{id}/chunks` that returns all chunk IDs for a document.

```java
// dto/ChunkListResponse.java
public record ChunkListResponse(Long documentId, List<String> chromaIds) {}

// controller/DocumentController.java — add inside the class
@GetMapping("/{id}/chunks")
public ChunkListResponse chunks(@PathVariable Long id) {
    return documentService.listChunks(id);
}

// service/DocumentService.java — add method
public ChunkListResponse listChunks(Long documentId) {
    documentRepository.findById(documentId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
    List<String> ids = chunkRepository.findByDocumentId(documentId)
        .stream().map(DocumentChunk::getChromaId).toList();
    return new ChunkListResponse(documentId, ids);
}
// DocumentNotFoundException already handled → 404; no new exception needed.
```

## Re-indexing after model switch

**Why it is needed:** each embedding model produces vectors in a distinct high-dimensional space.
OpenAI `text-embedding-3-small` produces 1536-dimensional vectors; Gemini `gemini-embedding-001`
produces 3072-dimensional vectors; Ollama `nomic-embed-text` produces 768-dimensional vectors.
After switching providers, ChromaDB contains vectors computed by the old model. Querying with
a new-model vector against old-model vectors returns semantically meaningless results.

**How to do it:**

```bash
# 1. Start with the new profile active
SPRING_PROFILES_ACTIVE=gemini ./mvnw spring-boot:run

# 2. Get all document IDs
curl http://localhost:8080/api/documents

# 3. Reindex each document (replace 1, 2, 3 with actual IDs)
curl -X POST http://localhost:8080/api/documents/1/reindex
curl -X POST http://localhost:8080/api/documents/2/reindex

# 4. Verify the active embedding dimension
curl http://localhost:8080/api/documents/stats
# → { "documentCount": 2, "chunkCount": 47, "embeddingDimensions": 3072 }
```

`reindex` uses the `extractedText` stored in PostgreSQL — no file re-upload is needed.
Documents with `status = FAILED` or no stored text must be re-uploaded.

## Resetting local data

Removes all PostgreSQL and ChromaDB data volumes:

```bash
docker compose down -v
```

Restart fresh:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Tables are recreated automatically on the next startup (`ddl-auto: update`).

## Common issues

| Symptom | Likely cause | Fix |
|---|---|---|
| `POST /api/documents/{id}/summary` returns 502 | `SummaryService` received prose instead of JSON from the model; JSON deserialization fails | Verify `PromptTemplates.SUMMARY_SYSTEM` still contains *"You MUST respond with valid JSON only"*. Check model logs for the raw response. |
| `POST /api/chat/ask` returns `"No answer found in the indexed documents."` despite indexed documents | Stale embeddings — documents were indexed with a different model than the one currently active | Reindex all documents (`POST /api/documents/{id}/reindex`). Check `GET /api/documents/stats` to confirm `embeddingDimensions`. |
| `NoUniqueBeanDefinitionException` on startup | Two AI starters both contributing beans (e.g. OpenAI + Ollama both active) | Confirm the Ollama starter is marked `<optional>true</optional>` in `pom.xml`. Only one model starter should be active at a time. |
| `Connection refused` to ChromaDB (port 8000) | Docker containers not running | Run `docker compose up -d` and verify with `docker compose ps`. |
| `Connection refused` to PostgreSQL (port 5432) | Docker containers not running | Same as above. |
| `org.postgresql.util.PSQLException: password authentication failed` | `DB_PASSWORD` env var does not match `POSTGRES_PASSWORD` in docker-compose | Set both to the same value, or remove both to use the default `rag`. |
| PDF upload returns 422 | The uploaded PDF has no extractable text layer (scanned image) | Use a PDF with a real text layer, or run OCR on the file before uploading. |
| Application starts but requests fail with 401/403 errors from OpenAI | `OPENAI_API_KEY` not set or invalid | `export OPENAI_API_KEY=sk-...` and restart. |
