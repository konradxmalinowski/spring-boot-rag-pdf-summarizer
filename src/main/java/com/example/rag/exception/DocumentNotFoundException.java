package com.example.rag.exception;

/**
 * Rzucany, gdy dokument o podanym ID nie istnieje -> 404.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long id) {
        super("Nie znaleziono dokumentu o id=" + id);
    }
}
