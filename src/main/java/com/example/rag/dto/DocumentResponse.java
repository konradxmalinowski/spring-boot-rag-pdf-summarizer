package com.example.rag.dto;

import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentStatus;

import java.time.Instant;

/**
 * Reprezentacja dokumentu zwracana w API (bez surowego tekstu).
 */
public record DocumentResponse(
        Long id,
        String filename,
        long fileSizeBytes,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getFileSizeBytes(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt()
        );
    }
}
