# Architecture

last_updated: 2026-06-25

## Overview

This is a Spring Boot 3 REST application that provides PDF document ingestion, RAG-based question answering, and document summarization. PDFs are uploaded, parsed into text chunks, embedded into vectors, and stored in ChromaDB. Questions are answered by embedding the query, retrieving the top-K matching chunks from ChromaDB, and sending them as context to an LLM. Document summaries are generated from the raw text stored in PostgreSQL. The default LLM provider is OpenAI; Ollama (local) and Gemini are available as drop-in profiles.

## Package structure

| Package | Responsibility |
|---|---|
| `controller` | HTTP layer — validates input, maps exceptions to HTTP status codes via `GlobalExceptionHandler`, delegates to services |
| `service` | Business logic — document lifecycle, PDF parsing, RAG pipeline, summarization |
| `vectorstore` | Single integration point with ChromaDB via Spring AI `VectorStore` |
| `embedding` | Thin wrapper exposing `EmbeddingModel.dimensions()` for diagnostics |
| `rag` | Prompt templates for RAG and summarization paths |
| `entity` | JPA entities: `Document`, `DocumentChunk`, `DocumentStatus` |
| `repository` | Spring Data JPA repositories for both entities |
| `dto` | Request/response records: `AskRequest`, `AskResponse`, `UploadResponse`, `DocumentResponse`, `SummaryResponse`, `StatsResponse`, `ApiError` |
| `config` | `ChatClientConfig` — builds the `ChatClient` bean from the auto-configured builder |
| `exception` | Domain exceptions (`DocumentNotFoundException`, `InvalidFileException`, `PdfProcessingException`, `AiServiceException`) + `GlobalExceptionHandler` |

## Layer diagram

```
HTTP request
    │
    ▼
┌──────────────────────────────────────┐
│  Controller layer                     │
│  DocumentController  ChatController   │  @Valid, ResponseEntity, HTTP status
└───────────┬──────────────┬───────────┘
            │              │
            ▼              ▼
┌───────────────┐  ┌───────────────┐
│ DocumentService│  │   RagService  │
│ (orchestration)│  │  SummaryService│
└──────┬────────┘  └───────┬───────┘
       │                   │
   ┌───┼───────────────────┼───────────────────────┐
   │   │                   │                       │
   ▼   ▼                   ▼                       ▼
┌──────────┐  ┌──────────────────┐  ┌──────────────────────┐
│JPA repos │  │  ChromaService   │  │  ChatClient          │
│(Postgres)│  │  (VectorStore)   │  │  (LLM: OpenAI /      │
│          │  │  ┌─────────────┐ │  │   Ollama / Gemini)   │
│Document  │  │  │EmbeddingModel│ │  └──────────────────────┘
│Chunk     │  │  │(auto-wired) │ │
└──────────┘  │  └─────────────┘ │
              └──────────────────┘
                      │
                      ▼
                  ChromaDB
                (port 8000)
```

## Data model

### `Document` — table `documents`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` (PK) | auto-generated identity |
| `filename` | `VARCHAR` | sanitized with `Paths.getFileName()` |
| `fileSizeBytes` | `BIGINT` | bytes of the uploaded file |
| `status` | `VARCHAR` (enum) | `UPLOADED` → `PROCESSING` → `INDEXED` or `FAILED` |
| `chunkCount` | `INT` | number of chunks stored in ChromaDB |
| `errorMessage` | `VARCHAR(1000)` | populated on `FAILED`, null otherwise |
| `createdAt` | `TIMESTAMP` | set on insert, never updated; guarded by `@PrePersist` |
| `updatedAt` | `TIMESTAMP` | updated on every state change; guarded by `@PreUpdate` |
| `extractedText` | `TEXT` | full raw text from PDFBox; enables summary and reindex without re-upload |

### `DocumentChunk` — table `document_chunks`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` (PK) | auto-generated identity |
| `document_id` | `BIGINT` (FK) | references `documents.id` |
| `chromaId` | `VARCHAR` | UUID assigned by ChromaDB; used to delete a specific chunk |
| `chunkIndex` | `INT` | zero-based position within the document |

### Relationship

`Document` 1-to-many `DocumentChunk`. `CascadeType.ALL` with `orphanRemoval = true` so
removing a `Document` also removes its `DocumentChunk` records in PostgreSQL. The ChromaDB
vectors are deleted explicitly before the JPA delete (via `chromaId`).

### Why two stores?

PostgreSQL holds structured metadata that is queried by ID, listed, and filtered — tasks that
relational databases handle efficiently. It also stores `extractedText`, which is too large and
unstructured for a vector store but needed for summarization and re-indexing.

ChromaDB holds the embedding vectors and supports approximate nearest-neighbor search — the
core of the retrieval step. It is not a replacement for a relational store: it has no
transactional guarantees, no foreign keys, and its query language is limited to metadata
filter expressions.

The two stores are linked by `documentId` (stored as a String in ChromaDB metadata) and by
`chromaId` (the ChromaDB UUID stored in PostgreSQL), which allows the application to delete
a document's vectors precisely without a full collection scan.

## Key design decisions

### `documentId` stored as String in ChromaDB metadata

**What:** `ChromaService.addChunks()` stores `documentId` as `String.valueOf(documentId)`,
not as a `Long`. The filter expression at search time uses `documentId == '<id>'`.

**Why:** The Spring AI ChromaDB filter parser compares values using strict type equality.
If `documentId` is stored as a numeric type but the filter value is a String (which is how
Spring AI serializes filter expressions), every per-document search returns zero results.
Storing and filtering as String sidesteps the type system entirely.

### `uploadAndIndex()` is `@Transactional`

**What:** The entire upload-and-index flow — PostgreSQL save, ChromaDB write, chunk save —
runs in a single Spring transaction.

**Why:** PostgreSQL and ChromaDB cannot participate in a distributed transaction (ChromaDB
has no XA support). The `@Transactional` boundary ensures that if the process crashes after
writing to ChromaDB but before writing to PostgreSQL, the whole PostgreSQL operation is rolled
back. A compensating cleanup on restart is not needed because the orphaned ChromaDB entries
are harmless until the next delete or reindex. The pattern prioritizes data visibility consistency
(an entry is either fully present or fully absent from the PostgreSQL view) over perfect
vector-store consistency.

### `TokenTextSplitter` instantiated per call

**What:** `PdfProcessingService.chunk()` calls `new TokenTextSplitter()` on every invocation
rather than holding a singleton.

**Why:** `TokenTextSplitter` accumulates internal state during a `split()` call and is not
thread-safe for concurrent use. Under concurrent PDF uploads, a shared instance would produce
incorrect splits or throw exceptions. Per-call instantiation is cheap relative to the I/O cost
of reading a PDF and calling the embedding API.

### `EmbeddingService.dimensions()` result is cached

**What:** `EmbeddingService` stores the result of `embeddingModel.dimensions()` in a
`volatile int` field and returns the cached value on subsequent calls.

**Why:** `embeddingModel.dimensions()` may call the embedding API (at least for models that do
not expose the dimension in their metadata). `GET /api/documents/stats` is a lightweight
health-check endpoint that should not trigger an API call on every request. The cached value
is valid for the lifetime of the process because the active model is fixed by configuration.

### `SUMMARY_SYSTEM` prompt demands JSON unconditionally

**What:** `PromptTemplates.SUMMARY_SYSTEM` contains the instruction *"You MUST respond with
valid JSON only — no prose, no markdown fences."* regardless of the document's language.

**Why:** Without this, the model follows the document's language (e.g. Polish, German) and
may return prose instead of JSON when the document is not in English. `SummaryService` calls
`.entity(SummaryResponse.class)`, which deserializes the response as JSON. A prose response
causes a JSON parse exception, which propagates as a 502 Bad Gateway. The unconditional JSON
instruction overrides language-following behavior.

## Spring AI integration

| Abstraction | Spring AI Interface | Where wired |
|---|---|---|
| LLM chat | `ChatClient` | `ChatClientConfig.chatClient()` — built from auto-configured `ChatClient.Builder`; injected into `RagService` and `SummaryService` |
| Embeddings | `EmbeddingModel` | auto-configured by the active starter; injected into `EmbeddingService` and used internally by `VectorStore` |
| Vector store | `VectorStore` | auto-configured by `spring-ai-starter-vector-store-chroma`; injected into `ChromaService` |
| PDF reader | `PagePdfDocumentReader` | instantiated per call in `PdfProcessingService.readPages()` |
| Text splitter | `TokenTextSplitter` | instantiated per call in `PdfProcessingService.chunk()` |
| Filter expression | `SearchRequest.filterExpression()` | used in `ChromaService.search()` for per-document scoping |
| Structured output | `ChatClient.call().entity(Class<T>)` | used in `SummaryService.summarize()` to deserialize `SummaryResponse` |

The `ChatClient.Builder` is provided by whichever AI starter is active. Switching from OpenAI
to Ollama only requires activating the `ollama` profile — no application code changes.

## LLM provider profiles

| Profile | Activation | Chat model | Embedding model | Embedding dims | Notes |
|---|---|---|---|---|---|
| *(default)* | none | `gpt-4o-mini` | `text-embedding-3-small` | 1536 | `OPENAI_API_KEY` required |
| `ollama` | `SPRING_PROFILES_ACTIVE=ollama` | `llama3.1` | `nomic-embed-text` | 768 | Ollama must be running locally |
| `gemini` | `SPRING_PROFILES_ACTIVE=gemini` | `gemini-2.0-flash` | `gemini-embedding-001` | 3072 | Uses OpenAI-compatible endpoint; `GEMINI_API_KEY` required |

**Re-indexing requirement:** embedding models from different providers produce vectors in
incompatible spaces with different dimensions. After switching profiles, existing ChromaDB
vectors are invalid. All documents must be reindexed before similarity search will return
correct results (`POST /api/documents/{id}/reindex` for each document).

Gemini uses the OpenAI starter's HTTP client pointed at `https://generativelanguage.googleapis.com`
with Gemini-specific path overrides (`completions-path`, `embeddings-path`). No extra Maven
dependency is needed.

The Ollama starter is declared `<optional>true</optional>` in `pom.xml`. Without this, both
OpenAI and Ollama starters contribute `ChatModel` and `EmbeddingModel` beans simultaneously,
causing `NoUniqueBeanDefinitionException` at startup. The optional flag prevents Spring Boot
from auto-configuring the Ollama beans unless the `ollama` profile is active.

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) catches exceptions before they reach the
servlet container and maps them to a uniform `ApiError` JSON body.

| Exception | HTTP status | Typical cause |
|---|---|---|
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` constraint failure on request body (e.g. blank `question`) |
| `InvalidFileException` | 400 Bad Request | Empty file, wrong MIME type, or file exceeds size limit |
| `MaxUploadSizeExceededException` | 413 Payload Too Large | Multipart request exceeds Spring's `max-request-size` |
| `DocumentNotFoundException` | 404 Not Found | Path variable `{id}` does not match any document in PostgreSQL |
| `PdfProcessingException` | 422 Unprocessable Entity | PDFBox cannot extract text (e.g. scanned image without text layer) |
| `AiServiceException` | 502 Bad Gateway | OpenAI / Ollama / ChromaDB call failed or returned unexpected output |
| `Exception` (catch-all) | 500 Internal Server Error | Unexpected error; no internal details are leaked to the client |

All responses share the same `ApiError` structure:
```json
{
  "timestamp": "2026-06-25T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Document not found: 42",
  "details": []
}
```
