package com.example.rag.exception;

/**
 * Rzucany, gdy wywołanie modelu AI (OpenAI/Ollama) lub vector store zawiedzie
 * -> 502/503. Owija oryginalny błąd, by nie wyciekały szczegóły do klienta.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
