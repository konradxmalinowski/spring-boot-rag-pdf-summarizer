package com.example.rag.service;

import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.exception.AiServiceException;
import com.example.rag.rag.PromptTemplates;
import com.example.rag.vectorstore.ChromaService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Realizuje ścieżkę RAG: retrieve (Chroma) -> augment (kontekst) -> generate (LLM).
 */
@Service
public class RagService {

    private final ChromaService chromaService;
    private final ChatClient chatClient;
    private final int topK;

    public RagService(ChromaService chromaService,
                      ChatClient chatClient,
                      @Value("${app.rag.top-k:4}") int topK) {
        this.chromaService = chromaService;
        this.chatClient = chatClient;
        this.topK = topK;
    }

    public AskResponse ask(AskRequest request) {
        // 1-2. Embedding pytania + similarity search robi ChromaService (VectorStore).
        List<Document> matches = chromaService.search(request.question(), topK, request.documentId());

        if (matches.isEmpty()) {
            return new AskResponse(
                    "Nie znalazłem odpowiedzi w zaindeksowanych dokumentach.",
                    List.of());
        }

        // 3. Zbuduj kontekst z najbardziej pasujących chunków.
        String context = matches.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n---\n" + b);

        // 4. Wyślij prompt do modelu.
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(PromptTemplates.RAG_SYSTEM)
                    .user(u -> u.text(PromptTemplates.RAG_USER)
                            .param("context", context)
                            .param("question", request.question()))
                    .call()
                    .content();
        } catch (Exception e) {
            throw new AiServiceException("Model nie zwrócił odpowiedzi", e);
        }

        if (answer == null || answer.isBlank()) {
            return new AskResponse("Model did not return a response.", List.of());
        }

        // 5. Zwróć odpowiedź wraz ze źródłami (fragmentami).
        List<String> sources = matches.stream().map(Document::getText).toList();
        return new AskResponse(answer, sources);
    }
}
