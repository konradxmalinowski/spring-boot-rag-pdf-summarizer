# Diagram przepływu RAG

## Indeksacja (upload → ChromaDB)

```
  Klient
    │  POST /api/documents/upload  (multipart: file=*.pdf)
    ▼
┌─────────────────────┐
│ DocumentController   │  walidacja na wejściu (typ/rozmiar/pusty)
└─────────┬───────────┘
          ▼
┌─────────────────────┐     ┌──────────────────────┐
│ DocumentService      │────▶│ PdfProcessingService │  PDFBox: odczyt + chunking
│ (orkiestracja)       │     └──────────────────────┘
│                      │            │ List<String> chunks
│                      │◀───────────┘
│                      │     ┌──────────────────────┐
│                      │────▶│ ChromaService        │  VectorStore.add()
│                      │     │  → EmbeddingModel    │  liczy embeddingi
│                      │     │  → ChromaDB          │  zapis wektorów + metadanych
│                      │     └──────────────────────┘
│                      │            │ List<chromaId>
│                      │◀───────────┘
│                      │     ┌──────────────────────┐
│                      │────▶│ PostgreSQL (JPA)     │  metadane Document + Chunk
└──────────────────────┘     └──────────────────────┘
          │ 201 Created { id, filename, status: INDEXED, chunkCount }
          ▼
        Klient
```

## Pytanie (RAG search)

```
  Klient
    │  POST /api/chat/ask   { "question": "O czym jest dokument?" }
    ▼
┌─────────────────────┐
│ ChatController       │  @Valid (question NotBlank)
└─────────┬───────────┘
          ▼
┌─────────────────────┐
│ RagService           │
│  1. retrieve ────────┼──▶ ChromaService.search(question, topK, documentId?)
│                      │       │  EmbeddingModel embedduje pytanie
│                      │       │  ChromaDB zwraca topK podobnych chunków
│  2. augment          │◀──────┘  List<Document> (fragmenty)
│     buduje KONTEKST  │
│  3. generate ────────┼──▶ ChatClient.prompt(system + kontekst + pytanie)
│                      │       │  OpenAI / Ollama
│                      │◀──────┘  odpowiedź
└─────────┬───────────┘
          │ 200 OK { answer, sources[] }
          ▼
        Klient
```

## Streszczanie

```
  POST /api/documents/{id}/summary
    ▼
  SummaryService → pobiera zapisany tekst z PostgreSQL (bez ponownego czytania PDF)
                 → ChatClient.call().entity(SummaryResponse.class)  (structured output)
    ▼
  200 OK { shortSummary, detailedSummary, keyPoints[] }
```

## Dlaczego dwa magazyny (Postgres + Chroma)?

- **ChromaDB** — jedyny vector store: embeddingi i similarity search (wymóg sekcji RAG).
- **PostgreSQL** — źródło prawdy o metadanych: lista dokumentów, status, liczba
  chunków, ID wpisów w Chromie (`chromaId`) potrzebne do precyzyjnego usuwania
  oraz surowy tekst do streszczeń/reindeksacji bez ponownego uploadu.

Łącznikiem jest `documentId` zapisany w metadanych każdego wektora w Chromie.
```
