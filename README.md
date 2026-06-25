# Spring AI RAG — PDF Q&A and Summarization

A Spring Boot 3 + Spring AI REST application that lets you:

- upload a **PDF** document,
- ask questions about it using **RAG** (Retrieval-Augmented Generation),
- generate **summaries** (short, detailed, key points).

**ChromaDB** is the vector store (embeddings + similarity search).
**PostgreSQL** stores document and chunk metadata. The default LLM is **OpenAI**,
with drop-in support for **Ollama** and **Gemini** via Spring profiles.

## Stack

| Layer        | Technology                              |
|--------------|-----------------------------------------|
| Language     | Java 17                                 |
| Framework    | Spring Boot 3.4.5                       |
| AI           | Spring AI 1.0.0 (OpenAI / Ollama / Gemini) |
| Vector store | ChromaDB                                |
| Metadata DB  | PostgreSQL 16 + Spring Data JPA         |
| PDF parsing  | spring-ai-pdf-document-reader (PDFBox)  |
| Build        | Maven (wrapper `./mvnw`)                |

## Architecture

```
controller  → service → { repository (JPA) | vectorstore (Chroma) | embedding | rag }
                 │
               entity / dto / config / exception
```

- **controller** — `DocumentController`, `ChatController` (REST, HTTP status codes)
- **service** — `DocumentService` (orchestration), `PdfProcessingService`,
  `RagService`, `SummaryService`
- **vectorstore** — `ChromaService` (single point of contact with ChromaDB)
- **embedding** — `EmbeddingService` (embedding model metadata)
- **rag** — `PromptTemplates` (prompt templates)
- **repository / entity** — `Document`, `DocumentChunk` + JPA repositories
- **dto** — request/response records + `ApiError`
- **config** — `ChatClientConfig`
- **exception** — `GlobalExceptionHandler` + domain exceptions

Full flow diagram: [`docs/rag-flow.md`](docs/rag-flow.md).

## Running the application

### 1. Requirements

- JDK 17
- Docker + Docker Compose
- OpenAI API key (for the default profile)

### 2. Start dependencies (PostgreSQL + ChromaDB)

```bash
docker compose up -d
```

- PostgreSQL: `localhost:5432` (db `ragdb`, user `rag`, password `rag` — override with `DB_PASSWORD` env var)
- ChromaDB: `localhost:8000`

### 3. Set the OpenAI API key

```bash
export OPENAI_API_KEY=sk-...
```

### 4. Start the application

```bash
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`. PostgreSQL tables are created automatically (`ddl-auto: update`).

### 5. (Optional) Switch the LLM

The default profile uses **OpenAI**. Two additional profiles are available:

**Ollama (local):**
```bash
# requires a running Ollama instance with these models:
ollama pull llama3.1
ollama pull nomic-embed-text

SPRING_PROFILES_ACTIVE=ollama ./mvnw spring-boot:run
```

**Gemini (Google AI Studio):**
```bash
# get a key from https://aistudio.google.com/apikey
export GEMINI_API_KEY=...

SPRING_PROFILES_ACTIVE=gemini ./mvnw spring-boot:run
```
> Gemini works through its OpenAI-compatible endpoint — no extra dependency needed.
> The profile redirects the OpenAI starter (chat: `gemini-2.0-flash`,
> embeddings: `gemini-embedding-001`).

> **After switching embedding models, reindex your documents** (`POST .../reindex`).
> OpenAI, Ollama, and Gemini use different vector spaces and dimensions
> (OpenAI 1536, Gemini `gemini-embedding-001` = 3072). Check the active dimension
> with `GET /api/documents/stats`.

## Endpoints

| Method | Path                             | Description                        | Status |
|--------|----------------------------------|------------------------------------|--------|
| POST   | `/api/documents/upload`          | Upload + index a PDF               | 201    |
| GET    | `/api/documents`                 | List all documents                 | 200    |
| GET    | `/api/documents/stats`           | Document and chunk counts          | 200    |
| GET    | `/api/documents/{id}`            | Document details                   | 200    |
| POST   | `/api/documents/{id}/summary`    | Generate summary                   | 200    |
| POST   | `/api/documents/{id}/reindex`    | Re-index document                  | 200    |
| DELETE | `/api/documents/{id}`            | Delete (from ChromaDB and Postgres) | 204   |
| POST   | `/api/chat/ask`                  | RAG search (question answering)    | 200    |

### Example requests (curl)

```bash
# Upload
curl -F "file=@document.pdf" http://localhost:8080/api/documents/upload

# Question (RAG, all documents)
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is this document about?"}'

# Question scoped to one document
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the conclusions?", "documentId": 1}'

# Summary
curl -X POST http://localhost:8080/api/documents/1/summary

# List, stats, delete
curl http://localhost:8080/api/documents
curl http://localhost:8080/api/documents/stats
curl -X DELETE http://localhost:8080/api/documents/1
```

### Example responses

`POST /api/chat/ask`:
```json
{
  "answer": "The document describes the RAG architecture and its applications.",
  "sources": ["chunk 1...", "chunk 2..."]
}
```

`POST /api/documents/{id}/summary`:
```json
{
  "shortSummary": "...",
  "detailedSummary": "...",
  "keyPoints": ["...", "...", "..."]
}
```

Error (uniform format):
```json
{
  "timestamp": "2026-06-12T16:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation error",
  "details": ["question: Field 'question' is required and must not be blank"]
}
```

## Validation and security

- file type check (MIME `application/pdf` or `.pdf` extension),
- file size limit (configured in `application.yml` + multipart limit),
- empty-file guard,
- request body validation (`@Valid`, `@NotBlank`),
- uploaded filename sanitised with `Paths.getFileName()` (path traversal prevention),
- AI/vector store error handling (`AiServiceException` → 502),
- PDF processing error handling (`PdfProcessingException` → 422),
- central `GlobalExceptionHandler` with correct HTTP status codes.

## Tests

```bash
./mvnw test
```

- **unit tests** (Mockito): `RagServiceTest`, `DocumentServiceTest`, `SummaryServiceTest`
- **REST integration tests** (`@WebMvcTest` + MockMvc): `RestApiIntegrationTest`

Tests do not require a running DB, AI provider, or ChromaDB — all external dependencies are mocked.

## Postman

Import [`postman/rag-collection.json`](postman/rag-collection.json).
Set the `documentId` variable after the first upload.

## Resetting data

```bash
docker compose down -v   # removes PostgreSQL and ChromaDB volumes
```
