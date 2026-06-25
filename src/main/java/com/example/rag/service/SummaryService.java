package com.example.rag.service;

import com.example.rag.dto.SummaryResponse;
import com.example.rag.entity.Document;
import com.example.rag.exception.AiServiceException;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.rag.PromptTemplates;
import com.example.rag.repository.DocumentRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Generuje streszczenie dokumentu (krótkie, szczegółowe, punkty kluczowe).
 *
 * Korzysta z surowego tekstu zapisanego przy indeksacji, więc nie wymaga
 * ponownego odczytu PDF-a. Tekst jest przycinany do limitu, by zmieścić się
 * w oknie kontekstu modelu.
 */
@Service
public class SummaryService {

    /** Bezpieczny limit znaków wysyłanych do modelu (proste zabezpieczenie kontekstu). */
    private static final int MAX_CHARS = 24_000;

    private final DocumentRepository documentRepository;
    private final ChatClient chatClient;

    public SummaryService(DocumentRepository documentRepository, ChatClient chatClient) {
        this.documentRepository = documentRepository;
        this.chatClient = chatClient;
    }

    public SummaryResponse summarize(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String text = document.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new AiServiceException(
                    "Dokument nie ma zapisanej treści — zindeksuj go ponownie", null);
        }
        // Efektywnie finalna zmienna do użycia w lambdzie poniżej.
        String content;
        if (text.length() > MAX_CHARS) {
            content = text.substring(0, MAX_CHARS) + "\n\n[Document truncated at 24,000 characters]";
        } else {
            content = text;
        }

        try {
            // .entity() zmusza model do zwrócenia JSON-a pasującego do SummaryResponse.
            return chatClient.prompt()
                    .system(PromptTemplates.SUMMARY_SYSTEM)
                    .user(u -> u.text(PromptTemplates.SUMMARY_USER).param("content", content))
                    .call()
                    .entity(SummaryResponse.class);
        } catch (Exception e) {
            throw new AiServiceException("Nie udało się wygenerować streszczenia", e);
        }
    }
}
