package com.example.rag.exception;

/**
 * Rzucany, gdy odczyt/przetworzenie PDF-a się nie powiedzie -> 422.
 */
public class PdfProcessingException extends RuntimeException {

    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
