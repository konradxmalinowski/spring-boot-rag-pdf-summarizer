package com.example.rag.exception;

/**
 * Thrown for invalid file uploads (wrong type, empty, or too large) -> maps to 400.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
