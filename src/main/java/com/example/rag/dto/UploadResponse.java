package com.example.rag.dto;

import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentStatus;

/**
 * Response returned after a PDF has been uploaded and indexed.
 */
public record UploadResponse(
        Long id,
        String filename,
        DocumentStatus status,
        int chunkCount
) {
    public static UploadResponse from(Document document) {
        return new UploadResponse(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getChunkCount()
        );
    }
}
