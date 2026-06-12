package com.example.rag.dto;

/**
 * Statystyki magazynu wiedzy: ile dokumentów i chunków jest zaindeksowanych,
 * oraz wymiar aktywnej przestrzeni embeddingów (pomaga wykryć niespójność
 * po przełączeniu modelu np. z OpenAI na Ollamę).
 */
public record StatsResponse(
        long documentCount,
        long chunkCount,
        int embeddingDimensions
) {
}
