package com.example.rag.dto;

import java.time.Instant;
import java.util.List;

/**
 * Jednolity format błędu zwracany przez GlobalExceptionHandler.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(Instant.now(), status, error, message, details);
    }
}
