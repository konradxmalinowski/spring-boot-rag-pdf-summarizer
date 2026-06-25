package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG search request: { "question": "What is the document about?" }
 * Optionally narrows the search to a single document via documentId.
 */
public record AskRequest(

        @NotBlank(message = "Field 'question' is required and must not be blank")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question,

        Long documentId
) {
}
