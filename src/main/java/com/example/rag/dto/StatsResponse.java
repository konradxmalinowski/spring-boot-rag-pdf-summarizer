package com.example.rag.dto;

/**
 * Knowledge store statistics: number of indexed documents and chunks,
 * plus the active embedding dimension (useful for detecting inconsistencies
 * after switching models, e.g. from OpenAI to Ollama).
 */
public record StatsResponse(
        long documentCount,
        long chunkCount,
        int embeddingDimensions
) {
}
