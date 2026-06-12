package com.example.rag.exception;

import com.example.rag.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Centralna obsługa wyjątków -> spójny format ApiError + poprawne statusy HTTP.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Błędy walidacji body (@Valid) -> 400 z listą pól. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Błąd walidacji", details);
    }

    /** Nieprawidłowy plik (pusty, zły typ, za duży) -> 400. */
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiError> handleInvalidFile(InvalidFileException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    /** Przekroczony limit rozmiaru uploadu (Spring/servlet) -> 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Plik przekracza dozwolony rozmiar", List.of());
    }

    /** Brak dokumentu -> 404. */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(DocumentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    /** Błąd przetwarzania PDF (np. skan bez tekstu) -> 422. */
    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ApiError> handlePdf(PdfProcessingException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of());
    }

    /** Błąd modelu AI lub vector store -> 502. */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAi(AiServiceException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), List.of());
    }

    /** Wszystko inne -> 500 (bez wycieku szczegółów). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Wystąpił nieoczekiwany błąd", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, details);
        return ResponseEntity.status(status).body(body);
    }
}
