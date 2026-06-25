package com.example.rag.exception;

/**
 * Thrown when an AI model call (OpenAI/Ollama) or vector store operation fails
 * -> maps to 502. Wraps the original exception to avoid leaking internals to the client.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
