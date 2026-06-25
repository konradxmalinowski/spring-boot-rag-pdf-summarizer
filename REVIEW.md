# Code Review

## Summary
Total findings: 12 (Critical: 5, Warning: 5, Info: 2)

---

## Findings

---

### [CRITICAL] Metadata filter type mismatch breaks per-document RAG search — ChromaService.java:66

**What:** `documentId` is stored in Chroma metadata as a `Long` (Java type), but the filter expression is built as a plain integer literal string: `"documentId == " + documentId`. Spring AI's filter expression text parser (`FilterExpressionTextParser`) parses integer literals into `Integer` (32-bit), not `Long` (64-bit). ChromaDB's where-clause filter then compares a stored `Long` value against an `Integer` value. ChromaDB stores metadata values with their JSON type; the Java `Long` is serialised as a JSON number, and the filter compares it with what the Spring AI converter emits. More critically, IDs above `Integer.MAX_VALUE` (2,147,483,647) will produce a parse error at the Spring AI layer, and for normal IDs the type mismatch may silently return 0 results.

**Impact:** `POST /api/chat/ask` with a `documentId` filter always returns an empty result set (or crashes), making the per-document search feature completely broken.

**Fix:** Store `document Id` as a `String` in metadata so the filter uses string comparison, which is unambiguous:

```java
// ChromaService.java – addChunks()
metadata.put(META_DOCUMENT_ID, String.valueOf(documentId));   // store as String

// ChromaService.java – search()
if (documentId != null) {
    // quote the value so the text parser treats it as a string literal
    request.filterExpression(META_DOCUMENT_ID + " == '" + documentId + "'");
}
```

---

### [CRITICAL] `uploadAndIndex()` is not transactional — DocumentService.java:55-71

**What:** The method calls `documentRepository.save()` three times (UPLOADED, PROCESSING, INDEXED/FAILED) with no enclosing `@Transactional` annotation. If the JVM crashes or the DB connection is lost between the second and third save, the document record is left in `PROCESSING` status permanently with no way to recover automatically. More importantly, if the ChromaDB write (line 65, `index()`) succeeds but the final `documentRepository.save()` fails, chunks exist in Chroma with no corresponding metadata row—vector search will return documents that the application cannot dereference.

**Impact:** Data split-brain between PostgreSQL and ChromaDB; zombie PROCESSING records; potential NPE in `DocumentResponse.from()` for orphaned chunks.

**Fix:** Annotate the method with `@Transactional` and restructure so the Chroma write happens inside the transaction scope, or—since Chroma is non-transactional—at minimum wrap the full method so the final DB save is atomic with the status transition:

```java
@Transactional
public Document uploadAndIndex(MultipartFile file) {
    validate(file);
    byte[] bytes = readBytes(file);
    Document document = documentRepository.save(
            new Document(file.getOriginalFilename(), bytes.length));
    try {
        document.markProcessing();
        documentRepository.save(document);
        index(document, bytes);
    } catch (RuntimeException e) {
        document.markFailed(truncate(e.getMessage()));
        documentRepository.save(document);
        throw e;
    }
    return document;
}
```

---

### [CRITICAL] `SummaryService.summarize()` will fail at runtime when the LLM response is not valid JSON — SummaryService.java:47-51

**What:** `chatClient.prompt()...call().entity(SummaryResponse.class)` relies on Spring AI's structured output converter, which instructs the model to return JSON and then deserialises it into `SummaryResponse`. However, `SUMMARY_SYSTEM` (PromptTemplates.java:31-38) only describes the *field names* in free text — it does **not** tell the model to return raw JSON. Without a format instruction containing an explicit JSON schema (which Spring AI's `BeanOutputConverter` normally appends automatically via the `.entity()` path), the model will often reply with prose like "Here is the summary: shortSummary: ...".

The `.entity()` path on `ChatClient` **does** append a format-instruction via `BeanOutputConverter`, so the auto-appended schema instruction arrives in addition to `SUMMARY_SYSTEM`. But the system prompt's instructions conflict with it: the system prompt says "write in the language of the document" (Polish), while the schema instruction says to return JSON. Models frequently obey the first instruction and return Polish prose, causing a `JsonParseException` wrapped in an `AiServiceException`, which returns HTTP 502 to the client.

**Impact:** `POST /api/documents/{id}/summary` returns 502 for non-English documents or any model that prioritises the system-prompt language instruction over the format instruction.

**Fix:** Remove the language-in-document instruction from `SUMMARY_SYSTEM` (the schema instruction must not be overridden) and ensure the system prompt explicitly requests JSON output:

```java
public static final String SUMMARY_SYSTEM = """
        You are a document summarisation expert.
        You MUST respond with valid JSON only — no prose, no markdown fences.
        Fields: shortSummary (2-3 sentences), detailedSummary (one paragraph),
        keyPoints (array of 3-7 strings).
        """;
```

---

### [CRITICAL] Both OpenAI and Ollama `ChatModel` / `EmbeddingModel` beans are created simultaneously without the Ollama profile — pom.xml:70-76 / application.yml:28-30

**What:** `pom.xml` declares both `spring-ai-starter-model-openai` and `spring-ai-starter-model-ollama` as compile-scope dependencies with no `<optional>true</optional>`. Both starters auto-configure their beans. `application.yml` sets `spring.ai.model.chat: openai` and `spring.ai.model.embedding: openai`—this deactivates the Ollama *chat* and *embedding* auto-configurations via `@ConditionalOnProperty`. However, the Ollama starter also registers an `OllamaChatModel` bean that is activated by the absence of a matching `spring.ai.model.chat` property when `matchIfMissing=true`—which does not apply here because the property *is present*. So in the default profile this is fine, but:

1. The Ollama HTTP client still attempts to connect to `http://localhost:11434` at startup if any Ollama `*Properties` beans are created, generating log noise or connection-refused errors depending on Ollama auto-configuration ordering.
2. In the `gemini` profile, `spring.ai.model.chat` is never set (it inherits `openai` from `application.yml`—see the comment in `application-gemini.yml` line 9), so both starters' ChatModel beans may be wired up causing a `NoUniqueBeanDefinitionException` for `ChatModel` unless auto-configuration ordering happens to avoid it.

**Impact:** Startup failures or unexpected bean wiring in the `gemini` profile; connection noise in the default profile.

**Fix:** Mark the Ollama dependency as optional so it is not included in the default build, or move it to a separate Maven profile. Alternatively, ensure each profile explicitly disables the unused provider:

```xml
<!-- pom.xml: make Ollama optional so it doesn't pollute the default classpath -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
    <optional>true</optional>
</dependency>
```

---

### [CRITICAL] `Document` entity has no `@PrePersist` / `@PreUpdate`; `createdAt` is never set for JPA-managed instances — Document.java:69-76

**What:** `createdAt` and `updatedAt` are assigned only in the constructor (line 74-75). JPA calls the protected no-arg constructor (line 65-67) when loading entities from the database—both fields remain `null` for detached/loaded instances until explicitly set. When `DocumentResponse.from(document)` is called on a document fetched from the DB, `getCreatedAt()` returns `null`, and Jackson serialises it as JSON `null`. More dangerously, `Document.touch()` (line 99) calls `this.updatedAt = Instant.now()` and is called by `markProcessing()`, `markFailed()`, and `markIndexed()`—but `createdAt` is never touched on update, so after a `reindex` call, `createdAt` in the DB stays at its original value (correct), but if the entity were ever persisted for the first time via the no-arg constructor path (e.g., in a test helper), `createdAt` would be null and a `NOT NULL` constraint violation would occur.

Additionally, the `@Column(nullable = false, updatable = false)` on `createdAt` (line 49-50) means Hibernate will never overwrite it after initial insert—correct—but the `NOT NULL` constraint will trigger a `ConstraintViolationException` if `createdAt` is null at insert time.

**Impact:** A correctly wired upload path assigns `createdAt` in the constructor before the first `save()`, so normal operation is fine. But this is a fragile design: any code path that creates a `Document` without going through the public constructor (e.g., reflection-based frameworks, integration test helpers) will produce a DB error or NPE in `DocumentResponse`.

**Fix:** Use JPA lifecycle callbacks to be robust:

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
    updatedAt = createdAt;
}

@PreUpdate
protected void onUpdate() {
    updatedAt = Instant.now();
}
```

---

## Warnings

---

### [WARNING] `reindex()` is not in sync with `uploadAndIndex()` — `persistChunks()` is called outside a Chroma+DB atomic boundary — DocumentService.java:76-96

**What:** `reindex()` is annotated `@Transactional` (line 75), which covers the Postgres operations. However, `chromaService.deleteByIds()` (inside `removeChunksFromStores()`, line 87) calls ChromaDB before the DB transaction commits. If the subsequent `chromaService.addChunks()` (inside `persistChunks()`, line 93) fails, the Postgres transaction rolls back (chunks rows restored), but the deleted Chroma entries are already gone—they are not restored. This leaves the document with valid DB chunk rows pointing to non-existent Chroma IDs.

**Impact:** After a failed reindex, `search()` will find no vectors for that document even though the DB claims it is `INDEXED`.

**Fix:** Structure reindex so Chroma operations happen after DB is committed (use `@TransactionalEventListener`), or catch failure and also delete the newly-written Chroma entries in the catch block.

---

### [WARNING] Filename used as-is from client input — path traversal in document name display — DocumentService.java:59

**What:** `file.getOriginalFilename()` (line 59-60) is stored directly to the database and returned in API responses. `getOriginalFilename()` is attacker-controlled and can contain path separators (`../../etc/passwd`). While the application never writes a file to disk with this name (files are stored as bytes), it is returned verbatim in `DocumentResponse.filename`. If a downstream consumer uses this filename to write a file to disk (e.g., when downloading), it becomes a path traversal vector. There is also no maximum length check on the filename.

**Impact:** Stored reflected path traversal value; potential exploitation by downstream consumers.

**Fix:** Strip path components before persisting:

```java
String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
Document document = documentRepository.save(new Document(safeName, bytes.length));
```

---

### [WARNING] `TokenTextSplitter` is a stateful instance field shared across threads — PdfProcessingService.java:23

**What:** `private final TokenTextSplitter splitter = new TokenTextSplitter()` is an instance field of a `@Service` singleton. `TokenTextSplitter` creates a `lazy EncodingRegistry` internally (line 49 of the Spring AI source). The `EncodingRegistry` uses lazy loading with no synchronisation visible in the public API. Concurrent PDF uploads could race on registry initialisation or internal splitter state.

**Impact:** Under concurrent load (multiple simultaneous uploads), splitting could produce garbled chunks or throw an unchecked exception.

**Fix:** Either declare `splitter` as a local variable inside `process()` and `chunk()` (cheap to construct since the registry is shared at JVM level via Jtokkit's static registry), or verify that `TokenTextSplitter` is thread-safe and document that assumption:

```java
// In process() and chunk(), use a local instance or verify thread-safety:
List<Document> split = new TokenTextSplitter().split(List.of(new Document(text)));
```

---

### [WARNING] `chatClient.prompt()...content()` can return `null` — RagService.java:57

**What:** `ChatClient.call().content()` is documented as potentially returning `null` when the model returns no content (e.g., safety filter triggered, empty response). The return value is used directly in `new AskResponse(answer, sources)` (line 63-64) without a null check. Jackson will serialise `null` as JSON `null` for the `answer` field, but callers expecting a non-null string will fail.

**Impact:** NPE in callers that call `.length()` or `.trim()` on `answer`; unexpected `null` in API response instead of a meaningful message.

**Fix:**
```java
String answer = chatClient.prompt()
        .system(PromptTemplates.RAG_SYSTEM)
        .user(u -> u.text(PromptTemplates.RAG_USER)
                .param("context", context)
                .param("question", request.question()))
        .call()
        .content();
if (answer == null || answer.isBlank()) {
    return new AskResponse("Model did not return a response.", List.of());
}
```

---

### [WARNING] `DocumentService.stats()` calls `embeddingService.dimensions()` on every request — EmbeddingService.java:28 / DocumentService.java:123

**What:** `EmbeddingModel.dimensions()` in Spring AI 1.0.0 makes a live HTTP call to the embedding provider (OpenAI/Gemini/Ollama) to ask for the dimension count each time it is called. `GET /api/documents/stats` triggers this on every request. Under load this adds an unnecessary round-trip to the AI provider for a value that is constant during the application lifetime.

**Impact:** Latency spike on every `GET /api/documents/stats`; potential API quota exhaustion.

**Fix:** Cache the dimensions value lazily in `EmbeddingService`:

```java
private volatile int cachedDimensions = -1;

public int dimensions() {
    if (cachedDimensions < 0) {
        cachedDimensions = embeddingModel.dimensions();
    }
    return cachedDimensions;
}
```

---

## Info

---

### [INFO] Hardcoded database credentials in `docker-compose.yml` and `application.yml` — docker-compose.yml:14-16 / application.yml:8-9

**What:** `POSTGRES_PASSWORD: rag` and `spring.datasource.password: rag` are hardcoded. If this repository is made public or the docker-compose file is shared, the credentials are immediately visible.

**Fix:** Use environment variable substitution in both files:
```yaml
# docker-compose.yml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-rag}

# application.yml
password: ${DB_PASSWORD:rag}
```

---

### [INFO] `SUMMARY_USER` prompt template sends the full extracted text without informing the model of truncation — SummaryService.java:43 / PromptTemplates.java:40-43

**What:** When `text.length() > MAX_CHARS` (24,000 chars), the content is silently truncated at a character boundary (line 43), potentially mid-sentence. The model receives incomplete text with no indication it is truncated, which can produce a summary with a missing conclusion or cut-off key points.

**Fix:** Append a note when the content is truncated:
```java
String content;
if (text.length() > MAX_CHARS) {
    content = text.substring(0, MAX_CHARS) + "\n\n[Document truncated at 24,000 characters]";
} else {
    content = text;
}
```
