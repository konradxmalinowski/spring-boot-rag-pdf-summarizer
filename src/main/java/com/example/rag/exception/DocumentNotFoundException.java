package com.example.rag.exception;

/**
 * Thrown when no document exists for the given ID -> maps to 404.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long id) {
        super("Document not found for id=" + id);
    }
}
