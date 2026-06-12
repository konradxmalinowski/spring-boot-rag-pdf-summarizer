# Spring AI RAG — pytania i streszczenia PDF

Aplikacja REST w Spring Boot 3 + Spring AI, która pozwala:

- wgrać dokument **PDF**,
- zadawać do niego pytania w architekturze **RAG** (Retrieval-Augmented Generation),
- generować **streszczenia** (krótkie, szczegółowe, punkty kluczowe).

Główny magazyn wiedzy to **ChromaDB** (embeddingi + similarity search).
**PostgreSQL** trzyma metadane dokumentów i chunków. Domyślny model to **OpenAI**,
z łatwą zamianą na **Ollama** (profil Springa).

> **Uwaga o wersji Javy:** specyfikacja wymagała Java 21, ale projekt celuje w
> **Java 17** (jedyny JDK na maszynie deweloperskiej). Spring Boot 3 i Spring AI 1.0
> w pełni działają na Javie 17 — funkcjonalnie nie ma różnicy. Aby przejść na 21,
> zmień `<java.version>` w `pom.xml`.

## Stack

| Warstwa        | Technologia                          |
|----------------|--------------------------------------|
| Język          | Java 17                              |
| Framework      | Spring Boot 3.4.5                     |
| AI             | Spring AI 1.0.0 (OpenAI / Ollama)    |
| Vector store   | ChromaDB                             |
| Metadane       | PostgreSQL 16 + Spring Data JPA      |
| Odczyt PDF     | spring-ai-pdf-document-reader (PDFBox)|
| Build          | Maven (wrapper `./mvnw`)             |

## Architektura (warstwy)

```
controller  → service → { repository (JPA) | vectorstore (Chroma) | embedding | rag }
                 │
               entity / dto / config / exception
```

- **controller** — `DocumentController`, `ChatController` (REST, statusy HTTP)
- **service** — `DocumentService` (orkiestracja), `PdfProcessingService`,
  `RagService`, `SummaryService`
- **vectorstore** — `ChromaService` (jedyny punkt kontaktu z ChromaDB)
- **embedding** — `EmbeddingService` (info o modelu embeddingów)
- **rag** — `PromptTemplates` (szablony promptów)
- **repository / entity** — `Document`, `DocumentChunk` + repozytoria JPA
- **dto** — request/response (rekordy) + `ApiError`
- **config** — `ChatClientConfig`
- **exception** — `GlobalExceptionHandler` + wyjątki domenowe

Pełny diagram przepływu: [`docs/rag-flow.md`](docs/rag-flow.md).

## Uruchomienie

### 1. Wymagania

- JDK 17
- Docker + Docker Compose
- Klucz OpenAI API (dla domyślnego profilu)

### 2. Wystartuj zależności (PostgreSQL + ChromaDB)

```bash
docker compose up -d
```

- PostgreSQL: `localhost:5432` (db `ragdb`, user/hasło `rag`/`rag`)
- ChromaDB: `localhost:8000`

### 3. Ustaw klucz OpenAI

```bash
export OPENAI_API_KEY=sk-...
```

### 4. Uruchom aplikację

```bash
./mvnw spring-boot:run
```

Aplikacja wstaje na `http://localhost:8080`. Tabele w Postgresie tworzą się
automatycznie (`ddl-auto: update`).

### 5. (Opcjonalnie) Zamiana modelu LLM

Domyślnie używany jest **OpenAI**. Dostępne są dwa dodatkowe profile:

**Ollama (lokalnie):**
```bash
# wymaga lokalnej Ollamy z modelami:
ollama pull llama3.1
ollama pull nomic-embed-text

SPRING_PROFILES_ACTIVE=ollama ./mvnw spring-boot:run
```

**Gemini (Google AI Studio):**
```bash
# klucz z https://aistudio.google.com/apikey
export GEMINI_API_KEY=...

SPRING_PROFILES_ACTIVE=gemini ./mvnw spring-boot:run
```
> Gemini działa przez endpoint zgodny z OpenAI — nie ma dodatkowej zależności,
> profil tylko przekierowuje startera OpenAI (chat: `gemini-2.0-flash`,
> embeddingi: `text-embedding-004`).

> **Po zmianie modelu embeddingów przeindeksuj dokumenty** (`POST .../reindex`),
> bo OpenAI / Ollama / Gemini mają różne przestrzenie i wymiary wektorów
> (OpenAI 1536, Gemini 768). Sprawdź aktywny wymiar przez `GET /api/documents/stats`.

## Endpointy

| Metoda | Ścieżka                          | Opis                              | Status |
|--------|----------------------------------|-----------------------------------|--------|
| POST   | `/api/documents/upload`          | Upload + indeksacja PDF           | 201    |
| GET    | `/api/documents`                 | Lista dokumentów                  | 200    |
| GET    | `/api/documents/stats`           | Liczba dokumentów/chunków         | 200    |
| GET    | `/api/documents/{id}`            | Szczegóły dokumentu               | 200    |
| POST   | `/api/documents/{id}/summary`    | Streszczenie                      | 200    |
| POST   | `/api/documents/{id}/reindex`    | Ponowna indeksacja                | 200    |
| DELETE | `/api/documents/{id}`            | Usunięcie (z ChromaDB i bazy)     | 204    |
| POST   | `/api/chat/ask`                  | RAG search (pytanie)              | 200    |

### Przykładowe requesty (curl)

```bash
# Upload
curl -F "file=@dokument.pdf" http://localhost:8080/api/documents/upload

# Pytanie (RAG)
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "O czym jest dokument?"}'

# Pytanie zawężone do jednego dokumentu
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Jakie są wnioski?", "documentId": 1}'

# Streszczenie
curl -X POST http://localhost:8080/api/documents/1/summary

# Lista, statystyki, usunięcie
curl http://localhost:8080/api/documents
curl http://localhost:8080/api/documents/stats
curl -X DELETE http://localhost:8080/api/documents/1
```

### Przykładowe odpowiedzi

`POST /api/chat/ask`:
```json
{
  "answer": "Dokument opisuje architekturę RAG i jej zastosowania.",
  "sources": ["fragment 1...", "fragment 2..."]
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

Błąd (jednolity format):
```json
{
  "timestamp": "2026-06-12T16:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Błąd walidacji",
  "details": ["question: Pole 'question' jest wymagane i nie może być puste"]
}
```

## Walidacja i bezpieczeństwo

- typ pliku (MIME `application/pdf` lub rozszerzenie `.pdf`),
- rozmiar pliku (limit w `application.yml` + limit multipart),
- ochrona przed pustym plikiem,
- walidacja body (`@Valid`, `@NotBlank`),
- obsługa błędów AI/vector store (`AiServiceException` → 502),
- obsługa błędów PDF (`PdfProcessingException` → 422),
- centralny `GlobalExceptionHandler` z poprawnymi statusami HTTP.

## Testy

```bash
./mvnw test
```

- **jednostkowe** (Mockito): `RagServiceTest`, `DocumentServiceTest`, `SummaryServiceTest`
- **integracyjne REST** (`@WebMvcTest` + MockMvc): `RestApiIntegrationTest`

Testy nie wymagają DB/AI/Chromy — zależności zewnętrzne są zamockowane.

## Postman

Zaimportuj [`postman/rag-collection.json`](postman/rag-collection.json).
Ustaw zmienną `documentId` po pierwszym uploadzie.

## Reset danych

```bash
docker compose down -v   # kasuje wolumeny Postgresa i Chromy
```
