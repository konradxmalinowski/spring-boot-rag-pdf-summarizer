package com.example.rag.entity;

/**
 * Status przetwarzania dokumentu w pipeline RAG.
 */
public enum DocumentStatus {
    /** Plik zapisany, jeszcze nie zaindeksowany. */
    UPLOADED,
    /** Trwa odczyt PDF / chunking / embedding. */
    PROCESSING,
    /** Chunki i embeddingi są w ChromaDB — dokument gotowy do pytań. */
    INDEXED,
    /** Przetwarzanie nie powiodło się (szczegóły w polu errorMessage). */
    FAILED
}
