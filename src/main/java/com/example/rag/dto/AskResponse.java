package com.example.rag.dto;

import java.util.List;

/**
 * RAG response: the model-generated answer plus the source chunks used —
 * useful for verification.
 */
public record AskResponse(
        String answer,
        List<String> sources
) {
}
