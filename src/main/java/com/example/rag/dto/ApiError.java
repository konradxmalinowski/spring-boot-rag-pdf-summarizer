package com.example.rag.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error response format returned by GlobalExceptionHandler.
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
