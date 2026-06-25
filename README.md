<h1 align="center">Spring AI RAG — PDF Q&A & Summarization</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring_AI-1.0.0-6DB33F?logo=spring&logoColor=white" alt="Spring AI">
  <img src="https://img.shields.io/badge/ChromaDB-vector_store-FF6F00" alt="ChromaDB">
  <img src="https://img.shields.io/badge/OpenAI-GPT-412991?logo=openai&logoColor=white" alt="OpenAI">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License">
</p>

<p align="center">
  A production-ready REST API for uploading PDF documents, asking natural-language questions over them, and generating AI-powered summaries — built with Spring Boot 3 and Spring AI.
</p>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [Project Structure](#project-structure)

---

## Overview

This project demonstrates a **Retrieval-Augmented Generation (RAG)** pipeline built entirely on Spring AI. Users upload PDF files; the application splits them into chunks, embeds each chunk into ChromaDB, and answers questions by retrieving the most relevant chunks before prompting the LLM.

Three LLM providers are supported out of the box via Spring profiles: **OpenAI** (default), **Ollama** (local), and **Gemini** (Google AI Studio).

---

## Features

- **PDF upload & indexing** — upload a PDF, automatically split into chunks and stored in ChromaDB
- **RAG Q&A** — ask questions over all documents or scope a query to a single document
- **AI summaries** — three summary modes per document: short, detailed, and key points
- **Multi-provider support** — switch between OpenAI, Ollama, and Gemini via a Spring profile
- **Metadata persistence** — document and chunk metadata stored in PostgreSQL
- **Security** — path traversal prevention, MIME validation, request body validation
- **Uniform error format** — all errors follow the same JSON structure with HTTP status codes

---

## Architecture

```
HTTP Request
     │
     ▼
┌─────────────────────────────────┐
│         Controllers             │
│  DocumentController             │
│  ChatController                 │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│           Services              │
│  DocumentService (orchestration)│
│  PdfProcessingService           │
│  RagService                     │
│  SummaryService                 │
└──────┬──────────────┬───────────┘
       │              │
       ▼              ▼
┌────────────┐  ┌─────────────────┐
│ PostgreSQL │  │    ChromaDB     │
│ (metadata) │  │ (vector store)  │
└────────────┘  └─────────────────┘
```

**Layer responsibilities:**

| Layer | Classes |
|---|---|
| Controller | `DocumentController`, `ChatController` |
| Service | `DocumentService`, `PdfProcessingService`, `RagService`, `SummaryService` |
| Vector store | `ChromaService` |
| Embedding | `EmbeddingService` |
| Prompts | `PromptTemplates` |
| Persistence | `Document`, `DocumentChunk`, JPA repositories |
| DTO | Request/response records, `ApiError` |
| Config | `ChatClientConfig` |
| Exceptions | `GlobalExceptionHandler`, domain exceptions |

Full data-flow diagram: [`docs/rag-flow.md`](docs/rag-flow.md) · Architecture details: [`docs/architecture.md`](docs/architecture.md)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.5 |
| AI | Spring AI 1.0.0 |
| LLM providers | OpenAI · Ollama · Gemini (via profiles) |
| Vector store | ChromaDB |
| Metadata DB | PostgreSQL 16 + Spring Data JPA |
| PDF parsing | spring-ai-pdf-document-reader (PDFBox) |
| Build | Maven (`./mvnw`) |
| Containers | Docker + Docker Compose |

---

## Getting Started

### Prerequisites

- JDK 17+
- Docker + Docker Compose
- An OpenAI API key (or Ollama / Gemini credentials for alternative profiles)

### 1. Clone the repository

```bash
git clone https://github.com/konradxmalinowski/spring-boot-rag-pdf-summarizer.git
cd spring-boot-rag-pdf-summarizer
```

### 2. Start infrastructure

```bash
docker compose up -d
```

Starts:
- **PostgreSQL** on `localhost:5432` (db: `ragdb`, user: `rag`, password: `rag`)
- **ChromaDB** on `localhost:8000`

Override the DB password with the `DB_PASSWORD` environment variable.

### 3. Set credentials

```bash
export OPENAI_API_KEY=sk-...
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. Database tables are created automatically on first start.

### 5. (Optional) Use a different LLM

**Ollama (local inference):**
```bash
ollama pull llama3.1
ollama pull nomic-embed-text

SPRING_PROFILES_ACTIVE=ollama ./mvnw spring-boot:run
```

**Gemini (Google AI Studio):**
```bash
export GEMINI_API_KEY=...

SPRING_PROFILES_ACTIVE=gemini ./mvnw spring-boot:run
```

> After switching embedding models, re-index existing documents (`POST /api/documents/{id}/reindex`).
> OpenAI uses 1536 dimensions, Gemini `gemini-embedding-001` uses 3072.

---

## API Reference

| Method | Endpoint | Description | Status |
|---|---|---|---|
| `POST` | `/api/documents/upload` | Upload and index a PDF | 201 |
| `GET` | `/api/documents` | List all documents | 200 |
| `GET` | `/api/documents/stats` | Document and chunk counts | 200 |
| `GET` | `/api/documents/{id}` | Get document details | 200 |
| `POST` | `/api/documents/{id}/summary` | Generate summary | 200 |
| `POST` | `/api/documents/{id}/reindex` | Re-index a document | 200 |
| `DELETE` | `/api/documents/{id}` | Delete document and its vectors | 204 |
| `POST` | `/api/chat/ask` | Ask a question (RAG) | 200 |

### Example requests

```bash
# Upload a PDF
curl -F "file=@document.pdf" http://localhost:8080/api/documents/upload

# Ask a question across all documents
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is this document about?"}'

# Ask scoped to one document
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the main conclusions?", "documentId": 1}'

# Generate summary
curl -X POST http://localhost:8080/api/documents/1/summary

# List documents, get stats, delete
curl http://localhost:8080/api/documents
curl http://localhost:8080/api/documents/stats
curl -X DELETE http://localhost:8080/api/documents/1
```

### Example responses

`POST /api/chat/ask`:
```json
{
  "answer": "The document describes the RAG architecture and its practical applications.",
  "sources": ["chunk 1 text...", "chunk 2 text..."]
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

Error response (all endpoints):
```json
{
  "timestamp": "2026-06-12T16:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation error",
  "details": ["question: must not be blank"]
}
```

A ready-made Postman collection is available at [`postman/rag-collection.json`](postman/rag-collection.json).

---

## Configuration

Key properties in `application.yml` / environment variables:

| Property | Env var | Default | Description |
|---|---|---|---|
| `spring.datasource.password` | `DB_PASSWORD` | `rag` | PostgreSQL password |
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | — | OpenAI API key |
| `spring.servlet.multipart.max-file-size` | — | `50MB` | Max upload size |

---

## Testing

```bash
./mvnw test
```

Test coverage:

| Type | Classes |
|---|---|
| Unit (Mockito) | `RagServiceTest`, `DocumentServiceTest`, `SummaryServiceTest` |
| REST integration (`@WebMvcTest`) | `RestApiIntegrationTest` |

All external dependencies (DB, ChromaDB, OpenAI) are mocked — tests run offline.

---

## Project Structure

```
src/
├── main/java/com/example/rag/
│   ├── controller/      # REST controllers
│   ├── service/         # Business logic
│   ├── vectorstore/     # ChromaDB integration
│   ├── embedding/       # Embedding model wrapper
│   ├── rag/             # Prompt templates
│   ├── repository/      # JPA repositories
│   ├── entity/          # JPA entities
│   ├── dto/             # Request / response records
│   ├── config/          # Spring configuration
│   └── exception/       # Exception handlers
├── main/resources/
│   └── application.yml
└── test/java/com/example/rag/
    └── service/ + integration/

docs/
├── architecture.md
├── development.md
└── rag-flow.md
```

---

## Resetting data

```bash
docker compose down -v   # removes PostgreSQL and ChromaDB volumes
```

---

## License

MIT
