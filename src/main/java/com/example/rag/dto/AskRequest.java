package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request do RAG search: { "question": "O czym jest dokument?" }
 * Opcjonalnie można zawęzić wyszukiwanie do jednego dokumentu (documentId).
 */
public record AskRequest(

        @NotBlank(message = "Pole 'question' jest wymagane i nie może być puste")
        @Size(max = 2000, message = "Pytanie może mieć maksymalnie 2000 znaków")
        String question,

        Long documentId
) {
}
