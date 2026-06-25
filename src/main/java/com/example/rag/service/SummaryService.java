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
 * Generates a document summary (short, detailed, and key points).
 *
 * Uses the raw text stored at indexing time, so no PDF re-read is needed.
 * The text is truncated to a safe limit to stay within the model's context window.
 */
@Service
public class SummaryService {

    /** Maximum characters sent to the model — a simple context-window guard. */
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
                    "Document has no stored text — please re-index it", null);
        }
        // Effectively-final variable for use in the lambda below.
        String content;
        if (text.length() > MAX_CHARS) {
            content = text.substring(0, MAX_CHARS) + "\n\n[Document truncated at 24,000 characters]";
        } else {
            content = text;
        }

        try {
            // .entity() instructs the model to return JSON matching SummaryResponse.
            return chatClient.prompt()
                    .system(PromptTemplates.SUMMARY_SYSTEM)
                    .user(u -> u.text(PromptTemplates.SUMMARY_USER).param("content", content))
                    .call()
                    .entity(SummaryResponse.class);
        } catch (Exception e) {
            throw new AiServiceException("Failed to generate document summary", e);
        }
    }
}
