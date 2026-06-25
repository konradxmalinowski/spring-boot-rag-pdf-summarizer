package com.example.rag.exception;

/**
 * Thrown when reading or processing a PDF fails -> maps to 422.
 */
public class PdfProcessingException extends RuntimeException {

    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
