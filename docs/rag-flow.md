# RAG Flow Diagram

## Indexing (upload → ChromaDB)

```
  Client
    │  POST /api/documents/upload  (multipart: file=*.pdf)
    ▼
┌─────────────────────┐
│ DocumentController   │  input validation (type / size / empty)
└─────────┬───────────┘
          ▼
┌─────────────────────┐     ┌──────────────────────┐
│ DocumentService      │────▶│ PdfProcessingService │  PDFBox: read + chunk
│ (orchestration)      │     └──────────────────────┘
│                      │            │ List<String> chunks
│                      │◀───────────┘
│                      │     ┌──────────────────────┐
│                      │────▶│ ChromaService        │  VectorStore.add()
│                      │     │  → EmbeddingModel    │  computes embeddings
│                      │     │  → ChromaDB          │  stores vectors + metadata
│                      │     │    (documentId: String)
│                      │     └──────────────────────┘
│                      │            │ List<chromaId>
│                      │◀───────────┘
│                      │     ┌──────────────────────┐
│                      │────▶│ PostgreSQL (JPA)     │  Document + DocumentChunk metadata
└──────────────────────┘     └──────────────────────┘
          │ 201 Created { id, filename, status: INDEXED, chunkCount }
          ▼
        Client
```

Key detail: `documentId` is written to ChromaDB metadata as a **String** (`String.valueOf(documentId)`).
This matches the filter expression used at search time (`documentId == '<id>'`), avoiding a type mismatch
in the Spring AI filter parser that would cause per-document searches to return zero results.

## Question answering (RAG search)

```
  Client
    │  POST /api/chat/ask   { "question": "What is this document about?", "documentId": "1" }
    ▼
┌─────────────────────┐
│ ChatController       │  @Valid (question NotBlank)
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│ RagService           │
│  1. retrieve ────────┼──▶ ChromaService.search(question, topK, documentId?)
│                      │       │  EmbeddingModel embeds the question
│                      │       │  ChromaDB returns topK similar chunks
│                      │       │  (filtered by documentId String if provided)
│  2. augment          │◀──────┘  List<Document> (text chunks)
│     builds CONTEXT   │
│  3. generate ────────┼──▶ ChatClient.prompt(system + context + question)
│                      │       │  OpenAI / Ollama / Gemini
│                      │◀──────┘  answer text
└─────────┬───────────┘
          │ 200 OK { answer, sources[] }
          ▼
        Client
```

RAG parameters (configurable in `application.yml`):
- `app.rag.top-k` — number of chunks retrieved per query (default: 4)
- `app.rag.similarity-threshold` — minimum similarity score (default: 0.0, no threshold)

## Summarization

```
  POST /api/documents/{id}/summary
    ▼
  SummaryService
    → loads stored extractedText from PostgreSQL (no PDF re-read)
    → truncates to 24,000 characters if necessary (context window guard)
    → ChatClient.prompt(SUMMARY_SYSTEM, SUMMARY_USER).call().entity(SummaryResponse.class)
         └─ SUMMARY_SYSTEM demands JSON unconditionally (prevents language override)
    ▼
  200 OK { shortSummary, detailedSummary, keyPoints[] }
```

## Re-indexing

```
  POST /api/documents/{id}/reindex
    ▼
  DocumentService.reindex()
    → loads extractedText from PostgreSQL
    → removes existing chunks from ChromaDB (by chromaId) and PostgreSQL
    → re-chunks the stored text with TokenTextSplitter
    → writes new chunks to ChromaDB (new embeddings under the active model)
    → updates DocumentChunk records in PostgreSQL
    ▼
  200 OK { id, filename, status: INDEXED, chunkCount }
```

Required after switching embedding models — OpenAI, Ollama, and Gemini produce
vectors in different spaces with different dimensions.

## Why two stores (PostgreSQL + ChromaDB)?

**ChromaDB** — vector store: embeddings and similarity search. Required for the RAG path.

**PostgreSQL** — source of truth for metadata: document list, processing status, chunk count,
ChromaDB entry IDs (`chromaId`) needed for precise deletion, and the raw extracted text
required for summaries and re-indexing without re-uploading the file.

The link between the two stores is `documentId` stored in the metadata of every ChromaDB vector.

```
  PostgreSQL documents.id  ──(String.valueOf)──▶  ChromaDB metadata.documentId
  PostgreSQL document_chunks.chromaId  ◀────────  ChromaDB document UUID
```
