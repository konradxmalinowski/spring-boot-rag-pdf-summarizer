package com.example.rag.exception;

/**
 * Rzucany przy nieprawidłowym pliku (zły typ, pusty, za duży) -> 400.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
